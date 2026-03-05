# Load Test Guide (Docker + k6)

This guide runs the mini-shop app, PostgreSQL, Redis, and a mock PG endpoint in Docker, then executes k6 lock-contention scenarios.

Detailed STAR troubleshooting notes: `docs/K6_TEST_TROUBLESHOOTING_STAR.md`

## 1) Start infrastructure

```powershell
docker compose up -d --build postgres redis pg-mock app
```

Check status:

```powershell
docker compose ps
docker compose logs -f app
```

## 2) Run k6 scenarios

### Scenario A: Inventory lock contention (oversell prevention)

Runs many concurrent `/api/orders` requests against one product with limited stock.

```powershell
docker compose run --rm k6 run `
  -e BASE_URL=http://app:8080 `
  -e VUS=40 `
  -e ITERATIONS=200 `
  -e STOCK=50 `
  -e ORDER_QTY=1 `
  /scripts/order-lock-contention.js
```

Expected result:
- A bounded number of successful orders (roughly by stock)
- Remaining requests fail with `409` and error code `I001` (`INSUFFICIENT_INVENTORY`)
- No oversell

### Scenario B: Payment confirm contention (same tossOrderId)

Runs concurrent `/api/payments/confirm` requests to the same payment.

```powershell
docker compose run --rm k6 run `
  -e BASE_URL=http://app:8080 `
  -e VUS=30 `
  -e ITERATIONS=30 `
  -e PAYMENT_KEY=pk_k6_lock_test `
  /scripts/payment-confirm-contention.js
```

Expected result:
- `200` responses should dominate
- Confirm operation remains idempotent under concurrency
- With current implementation, first request can hold DB lock while external PG call is in-flight

### Scenario C: Reverse item ordering contention (lock ordering risk)

Runs concurrent `/api/orders` requests where half of VUs send `[A,B]` and half send `[B,A]`.
This is designed to expose deadlock risk when lock acquisition order is not normalized.

```powershell
docker compose run --rm k6 run `
  -e BASE_URL=http://app:8080 `
  -e VUS=80 `
  -e ITERATIONS=800 `
  -e STOCK_PER_PRODUCT=6000 `
  -e ORDER_QTY=1 `
  -e MAX_ERROR_RATE=0.01 `
  /scripts/order-lock-ordering-risk.js
```

Result interpretation:
- Threshold failure on `order_server_error_rate`, `order_transport_failure_rate`, or `order_unexpected_failure_rate` indicates instability under reverse ordering contention.
- Confirm with app logs:

```powershell
docker compose logs app --since=10m | Select-String "deadlock detected|CannotAcquireLockException|SQLState: 40P01"
```

## 3) Stop environment

```powershell
docker compose down -v
```

## Notes

- Docker profile: `src/main/resources/application-docker.properties`
- Mock PG endpoint: WireMock on `http://localhost:8089`
- Test DB: PostgreSQL 16 (for realistic row-lock behavior compared to H2)
- If you change production lock strategy, rerun both scenarios to compare p95/p99 latency and error patterns.
- If you changed app code, rebuild app image before rerun:

```powershell
docker compose build --no-cache app
docker compose up -d app
```

## Latest Results (2026-02-11)

### Inventory lock contention (VUS=40, ITERATIONS=200, STOCK=50, ORDER_QTY=1)

- Scenario: `/scripts/order-lock-contention.js`
- checks: `100%` (`206/206`)
- `order_success`: `50` (stock 50과 일치)
- `order_insufficient_inventory`: `150` (`409 I001` 기대 케이스)
- `http_req_failed`: `73.52%` (`150/204`)
- `http_req_duration`: avg `181.48ms`, p95 `583.34ms`
- `iteration_duration`: avg `185.18ms`, p95 `583.90ms`

Interpretation:
- 기능 관점에서는 과잉판매 없이 정상 동작(성공 50건 + 재고부족 150건).
- `http_req_failed`는 k6 기본 규칙에서 `4xx`를 실패로 집계하기 때문에 높게 보이는 값이며, 본 시나리오에서는 의도된 `409`를 포함한다.

### Payment confirm contention (VUS=100, ITERATIONS=100, same tossOrderId)

- Environment: Docker Compose (`app + postgres + redis + pg-mock + k6`)
- Result status: all checks passed in both runs

#### Run A: `fixedDelayMilliseconds=800`

- checks: `100%` (`211/211`)
- `http_req_failed`: `0.00%` (`0/106`)
- `confirm_success`: `100`
- `confirm_duration_ms`: avg `1022.64`, p95 `1120.17`, max `1136.66`
- `http_req_duration`: avg `968.74ms`, p95 `1.11s`
- `iteration_duration`: avg `1.02s`, p95 `1.12s`

#### Run B: `fixedDelayMilliseconds=2000` (sensitivity)

- checks: `100%` (`211/211`)
- `http_req_failed`: `0.00%` (`0/106`)
- `confirm_success`: `100`
- `confirm_duration_ms`: avg `2408.89`, p95 `2479.76`, max `2492.84`
- `http_req_duration`: avg `2.27s`, p95 `2.47s`
- `iteration_duration`: avg `2.42s`, p95 `2.49s`

Interpretation:
- No functional failure under `100x100` contention after rebuild.
- Latency increases roughly in proportion to external PG delay (expected behavior while confirm path holds lock during external call).

### Reverse item ordering contention (VUS=80, ITERATIONS=800)

- Scenario: half requests `[A,B]`, half `[B,A]` (`scripts/k6/order-lock-ordering-risk.js`)
- k6 summary (latest run):
  - `order_success`: `398/800` (49.75%)
  - `order_unexpected_failure`: `402/800` (50.25%)
  - `http_req_failed`: `49.87%` (`402/806`)
- app logs in same time window showed repeated deadlock signatures:
  - `deadlock detected`
  - `SQLState: 40P01`
  - `CannotAcquireLockException`

Interpretation:
- Reverse lock ordering under concurrent multi-item order creation reproduces DB deadlock risk.
- This supports introducing a deterministic lock order (e.g., sort by `productId`) and retry handling for deadlock errors.

## Lock Hold-Time Reduction Plan (Payment Confirm)

Scenario A is useful for inventory lock behavior (oversell prevention), but it does not measure payment confirm lock hold-time directly.
To validate payment lock-hold optimization, use Scenario B with the same load profile before/after code changes.

### Goal
- Verify whether moving external PG confirm call out of DB transaction reduces lock-related failures and contention cost.

### Test Matrix (Before vs After)
- Before: single transaction confirm flow (lock + external PG call in same transaction)
- After: split transaction confirm flow
  - Tx1: lock + validation + `PROCESSING`
  - External PG call (outside transaction)
  - Tx2 (`REQUIRES_NEW`): finalize `COMPLETED` or `FAILED`

### Run Command (same for both versions)
```powershell
docker compose run --rm k6 run `
  -e BASE_URL=http://app:8080 `
  -e VUS=100 `
  -e ITERATIONS=100 `
  -e PAYMENT_KEY=pk_k6_lock_test `
  /scripts/payment-confirm-contention.js
```

### Metrics to Compare
- k6:
  - `checks` pass rate
  - `http_req_failed`
  - `confirm_duration_ms` (avg, p95, p99)
  - `http_req_duration` (avg, p95, p99)
- app logs:
  - `PG confirm failed`
  - lock/deadlock related errors (`CannotAcquireLockException`, `deadlock detected`, `SQLState: 40P01`)

### Interpretation Guide
- If `checks` improves and `http_req_failed` drops while p95/p99 remain stable or improve, lock-hold optimization is effective.
- If p95/p99 worsens but failures drop, you likely traded synchronous waiting for stability; evaluate with business SLA.
- For lock-order/deadlock risk in order creation, keep Scenario C as a separate guardrail test.

### Expected Impact on Existing k6 Scripts After This Change
- `scripts/k6/payment-confirm-contention.js`
  - Functional expectation stays the same (`200 + success=true`), but failure patterns from lock-hold side effects should decrease.
  - Latency may still reflect PG delay because concurrent callers wait for the same payment to finalize.
- `scripts/k6/order-lock-contention.js` (Scenario A)
  - Core success/failure distribution should remain stock-bounded; no oversell behavior should be unchanged.
  - Small latency changes are possible due to lock-order normalization overhead, but should be minor.
- `scripts/k6/order-lock-ordering-risk.js` (Scenario C)
  - Deadlock-related failures are expected to decrease after product-id-based lock ordering.

## Comparison Run (2026-02-11, Post-change)

Executed with the same parameters as baseline.

### Scenario A (Inventory contention)
- checks: `100%` (`206/206`)  -> same as baseline
- `order_success`: `50`, `order_insufficient_inventory`: `150`  -> same as baseline (no oversell)
- `http_req_duration`: avg `173.24ms`, p95 `559.70ms` (baseline avg `181.48ms`, p95 `583.34ms`)

### Scenario B (Payment confirm contention, VUS=100/ITER=100)
- Run #1:
  - checks: `91.46%` (`193/211`)
  - `http_req_failed`: `8.49%` (`9/106`)
  - `confirm_success`: `91`, `confirm_unexpected_failure`: `9`
  - `confirm_duration_ms`: avg `1922.22`, p95 `10100.85`, max `10103.39`
- Run #2:
  - checks: `91.46%` (`193/211`)
  - `http_req_failed`: `8.49%` (`9/106`)
  - `confirm_success`: `91`, `confirm_unexpected_failure`: `9`
  - `confirm_duration_ms`: avg `1921.24`, p95 `10099.12`, max `10142.38`

Comparison to baseline:
- Baseline was `checks=100%`, `http_req_failed=0%`, `confirm_success=100`.
- Post-change shows a repeatable regression under this load profile (`9/100` failures with ~10s timeout-like tail latency).

### Scenario C (Reverse ordering risk, VUS=80/ITER=800)
- checks: `100%` (`809/809`)
- `http_req_failed`: `0%` (`0/806`)
- `order_success`: `800/800`
- `order_server_error_rate`: `0%`
- `order_transport_failure_rate`: `0%`
- `order_unexpected_failure_rate`: `0%`
- app log scan: no `deadlock detected`, `CannotAcquireLockException`, `SQLState: 40P01`

Comparison to baseline:
- Baseline had ~`49.87%` request failure and deadlock signatures.
- Post-change indicates lock-order normalization effectively removed the reproduced deadlock pattern in this scenario.

## Re-test After Wait-Path Fix (2026-02-11)

Applied fix summary:
- `prepareConfirm` now does a fast non-lock read for `COMPLETED/PROCESSING` and only acquires row lock for `REQUESTED -> PROCESSING`.
- `waitForCompletion` clears persistence context each poll to avoid stale first-level cache reads.

Scenario B rerun (VUS=100, ITERATIONS=100, same parameters):
- Run #1:
  - checks: `100%` (`211/211`)
  - `http_req_failed`: `0%` (`0/106`)
  - `confirm_success`: `100`
  - `confirm_duration_ms`: avg `1247.72`, p95 `1310.97`, max `1347.86`
- Run #2:
  - checks: `100%` (`211/211`)
  - `http_req_failed`: `0%` (`0/106`)
  - `confirm_success`: `100`
  - `confirm_duration_ms`: avg `952.99`, p95 `998.75`, max `1047.27`

Interpretation:
- The previous regression (`91/100`, ~10s tail for 9 requests) was resolved.
- The fix is reproducible under the same load profile.
