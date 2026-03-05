import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 40);
const ITERATIONS = Number(__ENV.ITERATIONS || 200);
const STOCK = Number(__ENV.STOCK || 50);
const ORDER_QTY = Number(__ENV.ORDER_QTY || 1);

const orderSuccess = new Counter('order_success');
const insufficientInventory = new Counter('order_insufficient_inventory');
const unexpectedFailure = new Counter('order_unexpected_failure');

export const options = {
  scenarios: {
    order_contention: {
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
  const email = `k6-order-${uid}@minishop.local`;
  const password = 'Passw0rd!';
  const name = 'k6-order-user';

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
    'access token exists': () => !!loginBody?.data?.accessToken,
  });
  const token = loginBody.data.accessToken;

  const productRes = postJson(`${BASE_URL}/api/products`, {
    name: `k6-product-${uid}`,
    description: 'k6 load test product',
    unitPrice: 1000,
  }, authHeaders(token));
  const productBody = parseJson(productRes);
  check(productRes, {
    'create product status is 200': (r) => r.status === 200,
    'product id exists': () => !!productBody?.data?.id,
  });
  const productId = productBody.data.id;

  const addStockRes = patchJson(`${BASE_URL}/api/inventories/${productId}/add-stock`, {
    quantity: STOCK,
  }, authHeaders(token));
  check(addStockRes, {
    'add stock status is 200': (r) => r.status === 200,
  });

  return { token, productId };
}

export default function (data) {
  const createOrderRes = postJson(`${BASE_URL}/api/orders`, {
    items: [{ productId: data.productId, quantity: ORDER_QTY }],
  }, authHeaders(data.token));

  const body = parseJson(createOrderRes);
  const isSuccess = createOrderRes.status === 200 && body?.success === true;
  const isExpectedConflict =
    createOrderRes.status === 409 && body?.success === false && body?.errorCode === 'I001';

  if (isSuccess) {
    orderSuccess.add(1);
  } else if (isExpectedConflict) {
    insufficientInventory.add(1);
  } else {
    unexpectedFailure.add(1);
  }

  check(createOrderRes, {
    'order status is success or expected conflict': () => isSuccess || isExpectedConflict,
  });
}

