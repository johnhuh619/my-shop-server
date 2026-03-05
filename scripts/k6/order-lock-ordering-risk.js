import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 80);
const ITERATIONS = Number(__ENV.ITERATIONS || 800);
const STOCK_PER_PRODUCT = Number(__ENV.STOCK_PER_PRODUCT || 5000);
const ORDER_QTY = Number(__ENV.ORDER_QTY || 1);
const MAX_ERROR_RATE = Number(__ENV.MAX_ERROR_RATE || 0.01);

const orderSuccess = new Counter('order_success');
const orderConflict = new Counter('order_conflict');
const orderServerError = new Counter('order_server_error');
const orderTransportFailure = new Counter('order_transport_failure');
const orderUnexpectedFailure = new Counter('order_unexpected_failure');
const orderDuration = new Trend('order_duration_ms');
const orderServerErrorRate = new Rate('order_server_error_rate');
const orderTransportFailureRate = new Rate('order_transport_failure_rate');
const orderUnexpectedFailureRate = new Rate('order_unexpected_failure_rate');

export const options = {
  scenarios: {
    reverse_order_contention: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '3m',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    order_server_error_rate: [`rate<=${MAX_ERROR_RATE}`],
    order_transport_failure_rate: [`rate<=${MAX_ERROR_RATE}`],
    order_unexpected_failure_rate: [`rate<=${MAX_ERROR_RATE}`],
  },
};

function parseJson(response) {
  try {
    return response.json();
  } catch (e) {
    return null;
  }
}

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

function postJson(url, body, headers) {
  return http.post(url, JSON.stringify(body), { headers });
}

function patchJson(url, body, headers) {
  return http.patch(url, JSON.stringify(body), { headers });
}

export function setup() {
  const uid = Date.now();
  const email = `k6-order-risk-${uid}@minishop.local`;
  const password = 'Passw0rd!';
  const name = 'k6-order-risk-user';

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
  const token = loginBody?.data?.accessToken;

  const productARes = postJson(`${BASE_URL}/api/products`, {
    name: `k6-risk-product-a-${uid}`,
    description: 'k6 lock ordering risk product A',
    unitPrice: 1000,
  }, authHeaders(token));
  const productABody = parseJson(productARes);
  check(productARes, {
    'product A status is 200': (r) => r.status === 200,
    'product A id exists': () => !!productABody?.data?.id,
  });
  const productAId = productABody?.data?.id;

  const productBRes = postJson(`${BASE_URL}/api/products`, {
    name: `k6-risk-product-b-${uid}`,
    description: 'k6 lock ordering risk product B',
    unitPrice: 1000,
  }, authHeaders(token));
  const productBBody = parseJson(productBRes);
  check(productBRes, {
    'product B status is 200': (r) => r.status === 200,
    'product B id exists': () => !!productBBody?.data?.id,
  });
  const productBId = productBBody?.data?.id;

  const addStockARes = patchJson(`${BASE_URL}/api/inventories/${productAId}/add-stock`, {
    quantity: STOCK_PER_PRODUCT,
  }, authHeaders(token));
  const addStockBRes = patchJson(`${BASE_URL}/api/inventories/${productBId}/add-stock`, {
    quantity: STOCK_PER_PRODUCT,
  }, authHeaders(token));
  check(addStockARes, {
    'add stock A status is 200': (r) => r.status === 200,
  });
  check(addStockBRes, {
    'add stock B status is 200': (r) => r.status === 200,
  });

  return { token, productAId, productBId };
}

export default function (data) {
  const isEvenVu = __VU % 2 === 0;
  const items = isEvenVu
    ? [
        { productId: data.productAId, quantity: ORDER_QTY },
        { productId: data.productBId, quantity: ORDER_QTY },
      ]
    : [
        { productId: data.productBId, quantity: ORDER_QTY },
        { productId: data.productAId, quantity: ORDER_QTY },
      ];

  const res = postJson(`${BASE_URL}/api/orders`, { items }, authHeaders(data.token));
  orderDuration.add(res.timings.duration);

  const body = parseJson(res);
  const isSuccess = res.status === 200;
  const isConflict = res.status === 409;
  const isServerError = res.status >= 500;
  const isTransportFailure = res.status === 0;
  const isExpected = isSuccess || isConflict || isServerError || isTransportFailure;

  if (isSuccess) {
    orderSuccess.add(1);
  } else if (isConflict) {
    orderConflict.add(1);
  } else if (isServerError) {
    orderServerError.add(1);
  } else if (isTransportFailure) {
    orderTransportFailure.add(1);
  } else {
    orderUnexpectedFailure.add(1);
  }
  orderServerErrorRate.add(isServerError);
  orderTransportFailureRate.add(isTransportFailure);
  orderUnexpectedFailureRate.add(!isExpected);

  check(res, {
    'order status is 200/409/5xx/0': () => isExpected,
  });
}
