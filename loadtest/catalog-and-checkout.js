// k6 load test: browsing traffic plus concurrent checkouts on the same products.
//
//   k6 run loadtest/catalog-and-checkout.js
//   k6 run -e BASE_URL=http://localhost:8088 loadtest/catalog-and-checkout.js
//
// All virtual users share one IP, so the per-IP rate limit (300 req/min) blocks most of the traffic
// with 429: that is the protection working. To measure raw capacity, raise the limit first:
//
//   RATE_LIMIT_API_PER_MINUTE=1000000 docker compose up -d api
//
// What to look at: p95 latency of the catalog, error rate, and that checkouts only fail
// with 409 (not enough stock), never with 500. Stock can never go negative.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';
const outOfStock = new Counter('checkout_rejected_no_stock');
const served = {};

export const options = {
  scenarios: {
    browsing: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 50 },
        { duration: '40s', target: 50 },
        { duration: '10s', target: 0 },
      ],
      exec: 'browse',
    },
    buyers: {
      executor: 'constant-vus',
      vus: 10,
      duration: '60s',
      exec: 'buy',
      startTime: '10s',
    },
  },
  thresholds: {
    'http_req_duration{scenario:browsing}': ['p(95)<300'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: 'customer@shopscale.dev', password: 'Customer123!' }),
    { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'logged in': (r) => r.status === 200 });
  return { token: res.json('token') };
}

export function browse() {
  const page = Math.floor(Math.random() * 3);
  const res = http.get(`${BASE_URL}/api/products?page=${page}&size=12&status=ACTIVE`);
  check(res, { 'catalog 200': (r) => r.status === 200 });
  const instance = res.headers['X-Served-By'];
  served[instance] = (served[instance] || 0) + 1;
  http.get(`${BASE_URL}/api/categories`);
  sleep(Math.random() * 1.5);
}

export function buy(data) {
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` };
  // Everyone competes for the same few products to create contention on the stock rows.
  const productId = 1 + Math.floor(Math.random() * 5);
  const res = http.post(`${BASE_URL}/api/orders`,
    JSON.stringify({ items: [{ productId, quantity: 1 }] }), { headers });
  check(res, { 'checkout 201 or 409': (r) => r.status === 201 || r.status === 409 });
  if (res.status === 409) {
    outOfStock.add(1);
  } else if (res.status === 201) {
    http.post(`${BASE_URL}/api/orders/${res.json('orderNumber')}/cancel`, null, { headers });
  }
  sleep(0.5);
}
