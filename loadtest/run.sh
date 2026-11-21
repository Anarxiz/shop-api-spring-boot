#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: bash loadtest/run.sh [options]
  --profile smoke|baseline|stress|spike|soak  (default: smoke)
  --workload catalog|mixed|login              (default: catalog)
  --rate 1..10000                            (default: 10)
  --users 1..500                             (default: 20)
  --skip-build                               Reuse the app image
  --redis                                    Enable the Redis catalog cache
  -h, --help                                 Show this help
EOF
}

die() {
    printf '%s\n' "$*" >&2
    exit 1
}

profile=smoke
workload=catalog
rate=10
users=20
up_args=(up -d --build)
compose_args=(-p shop-loadtest -f compose.yml)

while (($#)); do
    case "$1" in
        --profile|--workload|--rate|--users)
            (($# >= 2)) && [[ $2 != -* ]] || die "Missing value for $1"
            case "$1" in
                --profile) profile=$2 ;;
                --workload) workload=$2 ;;
                --rate) rate=$2 ;;
                --users) users=$2 ;;
            esac
            shift 2
            ;;
        --skip-build) up_args=(up -d); shift ;;
        --redis) compose_args+=(-f ../compose.redis.yml); shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "Unknown argument: $1. See --help." ;;
    esac
done

case "$profile" in
    smoke|baseline|stress|spike|soak) ;;
    *) die "Unknown profile: $profile" ;;
esac
case "$workload" in
    catalog|mixed|login) ;;
    *) die "Unknown workload: $workload" ;;
esac
[[ $rate =~ ^[0-9]{1,5}$ ]] && ((10#$rate >= 1 && 10#$rate <= 10000)) || die '--rate must be between 1 and 10000'
[[ $users =~ ^[0-9]{1,3}$ ]] && ((10#$users >= 1 && 10#$users <= 500)) || die '--users must be between 1 and 500'

command -v docker >/dev/null || die 'Docker is required'
command -v curl >/dev/null || die 'curl is required'
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

compose() {
    # Git Bash must pass container paths like /seed.sql unchanged to Docker.
    MSYS2_ARG_CONV_EXCL='*' docker compose "${compose_args[@]}" "$@"
}

compose "${up_args[@]}" postgres app prometheus || die 'Failed to start load-test stand'

printf 'Waiting for the isolated API at http://localhost:18080 ...\n'
deadline=$((SECONDS + 180))
until curl --fail --silent --max-time 5 --output /dev/null http://127.0.0.1:18080/api/categories; do
    ((SECONDS < deadline)) || die 'API did not become ready. Check: docker compose -p shop-loadtest -f loadtest/compose.yml logs app'
    sleep 2
done

compose exec -T postgres psql -U postgres -d shop -v ON_ERROR_STOP=1 -f /seed.sql || die 'Failed to seed the load-test database'

mkdir -p results
report="$PWD/results/$(date +%Y%m%d-%H%M%S)-$profile-$workload.log"
printf 'Prometheus: http://localhost:19090 | Report: %s\n' "$report"
if compose run --rm --no-deps -T k6 run --summary-mode full \
    -e "PROFILE=$profile" -e "WORKLOAD=$workload" -e "RATE=$rate" -e "USERS=$users" \
    /scripts/shop.js 2>&1 | tee "$report"; then
    exit 0
else
    statuses=("${PIPESTATUS[@]}")
    if ((statuses[0] != 0)); then
        printf 'k6 failed or a threshold was exceeded (exit %s). See %s\n' "${statuses[0]}" "$report" >&2
        exit "${statuses[0]}"
    fi
    die "Failed to save report: $report"
fi
