import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import execution from 'k6/execution';

const baseUrl = (__ENV.BASE_URL || 'http://app:8080').replace(/\/$/, '');
const profile = __ENV.PROFILE || 'smoke';
const workload = __ENV.WORKLOAD || 'catalog';
const rate = positiveInteger('RATE', 10);
const users = positiveInteger('USERS', profile === 'smoke' ? 2 : 20);
if (users > 500) throw new Error('USERS must be <= 500');
if (!['catalog', 'mixed', 'login'].includes(workload)) throw new Error('Unknown WORKLOAD');

const flowFailed = new Rate('flow_failed');
const flowDuration = new Trend('flow_duration', true);
http.setResponseCallback(http.expectedStatuses(200, 201));

const arrival = {
  executor: 'ramping-arrival-rate',
  startRate: 1,
  timeUnit: '1s',
  preAllocatedVUs: positiveInteger('PRE_ALLOCATED_VUS', 30),
  maxVUs: positiveInteger('MAX_VUS', 100),
  gracefulStop: '30s',
  tags: { phase: 'load', workload },
};
const stages = {
  smoke: [{ duration: '30s', target: 1 }],
  baseline: [
    { duration: '2m', target: rate },
    { duration: '5m', target: rate },
    { duration: '1m', target: 0 },
  ],
  stress: [
    { duration: '2m', target: rate },
    { duration: '2m', target: rate * 2 },
    { duration: '2m', target: rate * 4 },
    { duration: '2m', target: rate * 8 },
    { duration: '2m', target: 0 },
  ],
  spike: [
    { duration: '1m', target: rate },
    { duration: '10s', target: rate * 5 },
    { duration: '1m', target: rate * 5 },
    { duration: '10s', target: rate },
    { duration: '2m', target: rate },
    { duration: '30s', target: 0 },
  ],
  soak: [
    { duration: '2m', target: rate },
    { duration: __ENV.SOAK_DURATION || '30m', target: rate },
    { duration: '1m', target: 0 },
  ],
};
if (!stages[profile]) throw new Error('Unknown PROFILE');

const catalogEndpoints = ['GET /api/categories', 'GET /api/dishes', 'GET /api/dishes/{id}',
  'GET /api/dishes/by-category/{id}'];
const measuredEndpoints = workload === 'catalog' ? catalogEndpoints : workload === 'login'
  ? ['POST /api/auth/login']
  : [...catalogEndpoints, 'POST /api/auth/login', 'POST /api/orders', 'GET /api/orders/{id}', 'GET /api/orders'];
const endpointThresholds = {};
for (const name of measuredEndpoints) {
  endpointThresholds[`http_req_duration{name:${name},phase:load}`] = ['p(95)<500', 'p(99)<1000'];
}

export const options = {
  scenarios: { traffic: { ...arrival, stages: stages[profile] } },
  setupTimeout: '3m',
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    ...endpointThresholds,
    'http_req_failed{phase:load}': ['rate<0.01'],
    'http_req_duration{phase:load}': ['p(95)<500', 'p(99)<1000'],
    flow_failed: [
      'rate<0.01',
      { threshold: 'rate<0.10', abortOnFail: true, delayAbortEval: '30s' },
    ],
    flow_duration: ['p(95)<1500'],
    dropped_iterations: ['count==0'],
  },
};

function positiveInteger(name, fallback) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isInteger(value) || value < 1) throw new Error(`${name} must be a positive integer`);
  return value;
}

function request(method, path, name, body = null, token = null, phase = 'load') {
  return http.request(method, baseUrl + path, body === null ? null : JSON.stringify(body), {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    tags: { name, phase },
    timeout: '10s',
    redirects: 0,
  });
}

function json(response) {
  try { return response.json(); } catch (_) { return null; }
}

function requireSetup(response, status, name) {
  if (response.status !== status) fail(`Setup failed: ${name}, HTTP ${response.status}`);
  const body = json(response);
  if (body === null) fail(`Setup failed: invalid JSON in ${name}`);
  return body;
}

export function setup() {
  const data = { accounts: [], dishes: [], categories: [] };
  if (workload !== 'login') {
    data.dishes = requireSetup(request('GET', '/api/dishes', 'setup dishes', null, null, 'setup'), 200, 'dishes');
    data.categories = requireSetup(request('GET', '/api/categories', 'setup categories', null, null, 'setup'), 200, 'categories');
    if (!Array.isArray(data.dishes) || !data.dishes.some(d => d.isAvailable) ||
        !Array.isArray(data.categories) || data.categories.length === 0) {
      fail('Seed the load-test database before running k6');
    }
    data.dishes = data.dishes.filter(d => d.isAvailable);
  }
  if (workload === 'catalog') return data;

  const runId = Date.now();
  for (let i = 0; i < users; i++) {
    const email = `load-${runId}-${i}@example.test`;
    const password = 'LoadTest123!';
    requireSetup(request('POST', '/api/auth/register', 'setup register', {
      firstName: 'Нагрузка', lastName: 'Тест', email, password,
      phone: '+7' + String(runId).slice(-7) + String(i).padStart(3, '0'),
    }, null, 'setup'), 200, 'register (rate limiting must be disabled on the test stand)');
    const login = requireSetup(request('POST', '/api/auth/login', 'setup login',
      { email, password }, null, 'setup'), 200, 'login');
    if (!login.token) fail('Setup login returned no token');
    const account = { email, password, token: login.token };
    if (workload === 'mixed') {
      account.addressId = requireSetup(request('POST', '/api/addresses', 'setup address', {
        city: 'Красноярск', street: 'Тестовая', house: '1', apartment: '1', floor: '1',
      }, account.token, 'setup'), 201, 'address');
    }
    data.accounts.push(account);
  }
  return data;
}

function pick(items) { return items[Math.floor(Math.random() * items.length)]; }

function catalog(data) {
  const choice = (execution.scenario.iterationInTest % 4) / 4;
  let response;
  if (choice < 0.25) response = request('GET', '/api/categories', 'GET /api/categories');
  else if (choice < 0.5) response = request('GET', '/api/dishes', 'GET /api/dishes');
  else if (choice < 0.75) response = request('GET', `/api/dishes/${pick(data.dishes).id}`, 'GET /api/dishes/{id}');
  else response = request('GET', `/api/dishes/by-category/${pick(data.categories).id}`, 'GET /api/dishes/by-category/{id}');
  return check(response, { 'catalog: HTTP 200 and JSON': r => r.status === 200 && json(r) !== null });
}

function login(data) {
  const account = pick(data.accounts);
  const response = request('POST', '/api/auth/login', 'POST /api/auth/login', {
    email: account.email, password: account.password,
  });
  return check(response, { 'login: HTTP 200 and token': r => r.status === 200 && !!json(r)?.token });
}

function checkout(data) {
  const account = pick(data.accounts);
  const dish = pick(data.dishes);
  const created = request('POST', '/api/orders', 'POST /api/orders', {
    addressId: account.addressId, items: [{ dishId: dish.id, quantity: 1 }],
  }, account.token);
  const id = json(created);
  if (!check(created, { 'order: HTTP 201 and UUID': r => r.status === 201 &&
      typeof id === 'string' && /^[0-9a-f-]{36}$/i.test(id) })) return false;
  const loaded = request('GET', `/api/orders/${id}`, 'GET /api/orders/{id}', null, account.token);
  const body = json(loaded);
  const valid = check(loaded, { 'order: ID and total are correct': r => r.status === 200 &&
    body?.orderId === id && Number(body.totalPrice) === Number(dish.price) });
  const history = request('GET', '/api/orders?page=0&size=20', 'GET /api/orders', null, account.token);
  const list = json(history);
  return check(history, { 'history: latest page contains 1–20 orders': r => r.status === 200 &&
    Array.isArray(list) && list.length > 0 && list.length <= 20 }) && valid;
}

export default function (data) {
  const start = Date.now();
  let ok = false;
  try {
    if (workload === 'catalog') ok = catalog(data);
    else if (workload === 'login') ok = login(data);
    else {
      const slot = execution.scenario.iterationInTest % 10;
      ok = slot < 7 ? catalog(data) : slot < 9 ? checkout(data) : login(data);
    }
  } finally {
    flowFailed.add(!ok);
    flowDuration.add(Date.now() - start);
  }
}
