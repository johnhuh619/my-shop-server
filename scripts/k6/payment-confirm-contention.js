import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 30);
const ITERATIONS = Number(__ENV.ITERATIONS || 30);
const STOCK = Number(__ENV.STOCK || 1);
const ORDER_QTY = Number(__ENV.ORDER_QTY || 1);
const PAYMENT_KEY = __ENV.PAYMENT_KEY || 'pk_k6_lock_test';

const confirmSuccess = new Counter('confirm_success');
const confirmUnexpectedFailure = new Counter('confirm_unexpected_failure');
const confirmDuration = new Trend('confirm_duration_ms');

export const options = {
  scenarios: {
    confirm_contention: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '2m',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
  },
};

function parseJson(response) {
  try {
    return response.json();
  } catch (e) {
    return null;
  }
}

function assertSetup(condition, message) {
  if (!condition) {
    throw new Error(`setup failed: ${message}`);
  }
}

function authHeaders(token, idempotencyKey) {
  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
  if (idempotencyKey) {
    headers['X-Idempotency-Key'] = idempotencyKey;
  }
  return headers;
}

function postJson(url, body, headers) {
  return http.post(url, JSON.stringify(body), { headers });
}

function patchJson(url, body, headers) {
  return http.patch(url, JSON.stringify(body), { headers });
}

export function setup() {
  const uid = Date.now();
  const email = `k6-confirm-${uid}@minishop.local`;
  const password = 'Passw0rd!';
  const name = 'k6-confirm-user';

  const registerRes = postJson(`${BASE_URL}/api/users/register`, { email, password, name }, {
    'Content-Type': 'application/json',
  });
  check(registerRes, {
    'register status is 200': (r) => r.status === 200,
  });

  const loginRes = postJson(`${BASE_URL}/api/auth/login`, { email, password }, {
    'Content-Type': 'application/json',
  });
  const loginBody = parseJson(loginRes);
  check(loginRes, {
    'login status is 200': (r) => r.status === 200,
    'login token exists': () => !!loginBody?.data?.accessToken,
  });
  assertSetup(loginRes.status === 200, `login status=${loginRes.status}`);
  assertSetup(!!loginBody?.data?.accessToken, `login response invalid: ${loginRes.body}`);
  const token = loginBody.data.accessToken;

  const productRes = postJson(`${BASE_URL}/api/products`, {
    name: `k6-confirm-product-${uid}`,
    description: 'k6 payment confirm contention',
    unitPrice: 1000,
  }, authHeaders(token));
  const productBody = parseJson(productRes);
  check(productRes, {
    'product status is 200': (r) => r.status === 200,
    'product id exists': () => !!productBody?.data?.id,
  });
  assertSetup(productRes.status === 200, `product status=${productRes.status}`);
  assertSetup(!!productBody?.data?.id, `product response invalid: ${productRes.body}`);
  const productId = productBody.data.id;

  const addStockRes = patchJson(`${BASE_URL}/api/inventories/${productId}/add-stock`, {
    quantity: STOCK,
  }, authHeaders(token));
  check(addStockRes, {
    'add stock status is 200': (r) => r.status === 200,
  });
  assertSetup(addStockRes.status === 200, `add stock status=${addStockRes.status}`);

  const orderRes = postJson(`${BASE_URL}/api/orders`, {
    items: [{ productId, quantity: ORDER_QTY }],
  }, authHeaders(token));
  const orderBody = parseJson(orderRes);
  check(orderRes, {
    'order status is 200': (r) => r.status === 200,
    'order id exists': () => !!orderBody?.data?.id,
  });
  assertSetup(orderRes.status === 200, `order status=${orderRes.status}`);
  assertSetup(!!orderBody?.data?.id, `order response invalid: ${orderRes.body}`);
  const orderId = orderBody.data.id;

  const prepareRes = postJson(`${BASE_URL}/api/payments`, {
    orderId,
  }, authHeaders(token, `k6-prepare-${uid}`));
  const prepareBody = parseJson(prepareRes);
  check(prepareRes, {
    'prepare status is 200': (r) => r.status === 200,
    'tossOrderId exists': () => !!prepareBody?.data?.tossOrderId,
    'amount exists': () => !!prepareBody?.data?.amount,
  });
  assertSetup(prepareRes.status === 200, `prepare status=${prepareRes.status}`);
  assertSetup(!!prepareBody?.data?.tossOrderId, `prepare tossOrderId missing: ${prepareRes.body}`);
  assertSetup(!!prepareBody?.data?.amount, `prepare amount missing: ${prepareRes.body}`);

  return {
    token,
    tossOrderId: prepareBody.data.tossOrderId,
    amount: prepareBody.data.amount,
  };
}

export default function (data) {
  const confirmRes = postJson(`${BASE_URL}/api/payments/confirm`, {
    paymentKey: PAYMENT_KEY,
    orderId: data.tossOrderId,
    amount: data.amount,
  }, authHeaders(data.token));

  confirmDuration.add(confirmRes.timings.duration);

  const body = parseJson(confirmRes);
  const ok = confirmRes.status === 200 && body?.success === true;

  if (ok) {
    confirmSuccess.add(1);
  } else {
    confirmUnexpectedFailure.add(1);
  }

  check(confirmRes, {
    'confirm status is 200': (r) => r.status === 200,
    'confirm success true': () => body?.success === true,
  });
}
