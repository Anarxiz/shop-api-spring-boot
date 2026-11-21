param(
    [ValidateSet('smoke', 'baseline', 'stress', 'spike', 'soak')]
    [string]$Profile = 'smoke',
    [ValidateSet('catalog', 'mixed', 'login')]
    [string]$Workload = 'catalog',
    [ValidateRange(1, 10000)]
    [int]$Rate = 10,
    [ValidateRange(1, 500)]
    [int]$Users = 20,
    [switch]$SkipBuild,
    [switch]$Redis
)

$ErrorActionPreference = 'Stop'
$composeArgs = @('compose', '--progress', 'plain', '-p', 'shop-loadtest', '-f', (Join-Path $PSScriptRoot 'compose.yml'))
if ($Redis) { $composeArgs += @('-f', (Join-Path $PSScriptRoot '../compose.redis.yml')) }
$upArgs = @('up', '-d')
if (-not $SkipBuild) { $upArgs += '--build' }
$upArgs += @('postgres', 'app', 'prometheus')
Write-Host 'Starting the isolated load-test stand ...'
& docker @composeArgs @upArgs
if ($LASTEXITCODE -ne 0) { throw 'Failed to start load-test stand' }

Write-Host 'Waiting for the isolated API at http://localhost:18080 ...'
$ready = $false
$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
    try {
        $null = Invoke-RestMethod 'http://127.0.0.1:18080/api/categories' -TimeoutSec 5
        $ready = $true
        break
    } catch { Start-Sleep -Seconds 2 }
}
if (-not $ready) { throw 'API did not become ready. Check: docker compose -p shop-loadtest -f loadtest/compose.yml logs app' }

Write-Host 'Loading test data ...'
& docker @composeArgs exec -T postgres psql -U postgres -d shop -v ON_ERROR_STOP=1 -f /seed.sql
if ($LASTEXITCODE -ne 0) { throw 'Failed to seed the load-test database' }

$resultsDir = Join-Path $PSScriptRoot 'results'
$null = New-Item -ItemType Directory -Path $resultsDir -Force
$reportPath = Join-Path $resultsDir ("{0}-{1}-{2}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss'), $Profile, $Workload)
Write-Host "Prometheus: http://localhost:19090 | Report: $reportPath"
# PowerShell 7 can treat native stderr as an ErrorRecord. Capture it as test output.
$ErrorActionPreference = 'Continue'
& docker @composeArgs run --rm --no-deps -T k6 run --summary-mode full `
    -e "PROFILE=$Profile" -e "WORKLOAD=$Workload" -e "RATE=$Rate" -e "USERS=$Users" `
    /scripts/shop.js 2>&1 | Tee-Object -FilePath $reportPath
$testExitCode = $LASTEXITCODE
if ($testExitCode -ne 0) {
    Write-Host "k6 failed or a threshold was exceeded (exit $testExitCode). See $reportPath"
}
exit $testExitCode
