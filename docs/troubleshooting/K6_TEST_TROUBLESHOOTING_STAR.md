# k6 Load Test Troubleshooting (STAR)

작성일: 2026-02-11  
범위: `scripts/k6/order-lock-contention.js`, `scripts/k6/payment-confirm-contention.js`, `scripts/k6/order-lock-ordering-risk.js`

## 목적
이 문서는 이번 락/결제 경합 이슈를 STAR 기법으로 정리한 트러블슈팅 기록이다.  
재현 커맨드, 관측 지표, 원인 특성(영속성 컨텍스트/락/트랜잭션)을 함께 남겨서 동일 증상 재발 시 빠르게 재현-분석-조치할 수 있도록 한다.

---

## 핵심 개념 해설

### 1) 영속성 컨텍스트(1차 캐시)와 stale read
- JPA는 같은 영속성 컨텍스트 안에서 동일 엔티티를 다시 조회하면 DB를 다시 읽지 않고 캐시된 엔티티를 반환할 수 있다.
- 폴링 루프가 같은 컨텍스트를 계속 사용하면, 다른 트랜잭션이 상태를 변경해도 내 조회가 늦게 반영될 수 있다.
- 이번 이슈에서는 `PROCESSING -> COMPLETED` 전환이 이미 끝났는데, 대기 루프가 오래 `PROCESSING`으로 보는 구간이 tail latency를 키웠다.

### 2) 비관 락(PESSIMISTIC_WRITE)과 락 보유 시간
- 비관 락은 동시 접근 순서를 강제로 직렬화해서 데이터 정합성에는 강하다.
- 대신 락 보유 시간이 길면 대기열이 길어지고, 타임아웃/지연 꼬리가 커진다.
- 외부 PG 호출처럼 느리고 변동성 큰 IO를 락 보유 트랜잭션 안에 두면 성능 리스크가 커진다.

### 3) 트랜잭션 전파(REQUIRED / REQUIRES_NEW)
- `REQUIRED`: 기존 트랜잭션 참여 또는 새 트랜잭션 시작.
- `REQUIRES_NEW`: 항상 새 트랜잭션 시작.
- 이번 구조는 "Tx1(상태 전이) -> 외부 호출(트랜잭션 밖) -> Tx2(최종 반영)"로 분리해 락 보유 시간을 줄였다.
- 트랜잭션 분리는 별도 Bean(`PaymentConfirmHandler`)에 선언적 `@Transactional`을 적용하는 방식으로 구현했다.

### 4) 데드락과 락 획득 순서
- 서로 다른 요청이 같은 자원을 다른 순서로 잠그면 순환 대기(cycle)가 생겨 데드락이 발생한다.
- 멀티 아이템 주문에서 `[A,B]`와 `[B,A]`가 동시에 들어오면 전형적인 데드락 패턴이 된다.
- 해결 핵심은 모든 요청이 동일 순서(예: productId 오름차순)로 락을 잡게 만드는 것이다.

### 5) k6 지표 해석 주의점
- `http_req_failed`는 4xx도 실패로 집계될 수 있다.
- 재고 테스트에서 `409 I001`은 기대된 도메인 결과(재고 부족)일 수 있으므로, 비즈니스 성공 기준(`order_success + expected_conflict`)과 분리해 해석해야 한다.

---

## Case 1. Payment Confirm 경합 회귀 (`91/100`, tail ~10s)

### S (Situation)
`payment-confirm-contention` 시나리오(`VUS=100`, `ITERATIONS=100`)에서 기존 `100/100`이던 성공률이 `91/100`으로 하락했다.  
관측값:
1. `checks=91.46%`
2. `http_req_failed=8.49% (9/106)`
3. `confirm_duration_ms p95 ≈ 10.1s`

### T (Task)
실패 9건과 10초대 tail latency 원인을 식별하고, 동일 부하에서 `100/100`으로 복구한다.

### A (Action)
1. `confirmPayment`를 상태 분기 기준으로 재점검했다.
2. `PROCESSING` 요청을 non-lock fast-path로 우선 처리하고, `REQUESTED -> PROCESSING` 전이에서만 row lock을 잡도록 변경했다.
3. `waitForCompletion` 폴링 루프에서 매회 `entityManager.clear()`로 1차 캐시를 비워 최신 상태를 읽도록 변경했다.
4. 동일 조건으로 2회 재실행해 재현성 확인.

관련 코드:
1. `src/main/java/com/minishop/project/minishop/payment/service/PaymentConfirmHandler.java:43` (`prepareConfirm`)
2. `src/main/java/com/minishop/project/minishop/payment/service/PaymentConfirmHandler.java:70` (`findByTossOrderIdWithLock` row lock)
3. `src/main/java/com/minishop/project/minishop/payment/service/PaymentConfirmHandler.java:146` (`waitForCompletion`)
4. `src/main/java/com/minishop/project/minishop/payment/service/PaymentConfirmHandler.java:151` (`entityManager.clear()`)

### R (Result)
동일 조건 2회 모두 정상화:
1. Run#1: `checks=100%`, `http_req_failed=0%`, `confirm_success=100`, `p95=1310.97ms`
2. Run#2: `checks=100%`, `http_req_failed=0%`, `confirm_success=100`, `p95=998.75ms`

### 개념 해설 (Why this fix)
- 문제 핵심은 "락 자체"보다, `PROCESSING` 대기 경로가 불필요하게 병목화되고 상태 관측이 늦어지는 구조였다.
- 상태 전이 시점만 락으로 보호하고, 대기 관측은 최신 스냅샷을 보게 분리함으로써 정합성과 처리량을 동시에 확보했다.

---

## Case 2. Reverse Ordering 데드락 (Scenario C)

### S (Situation)
`order-lock-ordering-risk` 시나리오에서 `[A,B]`와 `[B,A]` 동시 요청 시 MySQL 데드락이 반복 재현됐다.  
관측값:
1. `http_req_failed ≈ 49.87%`
2. 로그: `Deadlock found when trying to get lock`, `SQLState: 40001`, `ErrorCode: 1213`

### T (Task)
동일 시나리오에서 데드락 재현을 제거하고, 서버 오류율을 0에 가깝게 만든다.

### A (Action)
1. `createOrder`에서 락 획득 순서를 `productId` 오름차순으로 정규화했다.
2. 시나리오 C에 오류율 지표/threshold를 추가해 결과를 명확히 판별했다.

관련 코드:
1. `src/main/java/com/minishop/project/minishop/order/service/OrderService.java:30`
2. `src/main/java/com/minishop/project/minishop/order/service/OrderService.java:35`
3. `scripts/k6/order-lock-ordering-risk.js:18`
4. `scripts/k6/order-lock-ordering-risk.js:33`

### R (Result)
동일 부하(`VUS=80`, `ITERATIONS=800`)에서:
1. `checks=100%`
2. `http_req_failed=0%`
3. `order_success=800/800`
4. 데드락 시그니처 로그 미검출

### 개념 해설 (Why this fix)
- 데드락은 주로 순환 대기로 생긴다.
- 요청별 락 순서를 단일 규칙으로 강제하면 순환 대기 그래프 자체가 끊긴다.

---

## Case 3. Scenario A에서 `http_req_failed`가 높게 보이는 해석 문제

### S (Situation)
`order-lock-contention`에서 `http_req_failed`가 높게 나오지만, 비즈니스적으로는 정상일 수 있었다.

### T (Task)
지표 오해를 막고, 기대된 실패와 시스템 실패를 분리한다.

### A (Action)
1. 성공 기준을 `order_success + order_insufficient_inventory(409/I001)`로 정의했다.
2. oversell 여부를 별도 확인 항목으로 고정했다.
3. 문서에 4xx 집계 해석 주의점을 명시했다.

### R (Result)
`VUS=40`, `ITERATIONS=200`, `STOCK=50`에서:
1. `order_success=50`
2. `order_insufficient_inventory=150`
3. oversell 없음

### 개념 해설 (Why this interpretation)
- 부하 테스트에서 모든 비-200이 시스템 장애를 의미하지 않는다.
- 도메인 규칙상 정상 거절(재고 부족)을 실패로 단정하면 잘못된 최적화를 유도할 수 있다.

---

## Case 4. PG Mock 연동 불안정 (EOF / RST_STREAM)

### S (Situation)
결제 confirm 부하 테스트 중 PG 호출에서 간헐적 transport 오류가 발생했다.
1. `EOF reached while reading`
2. `Received RST_STREAM: Stream cancelled`

### T (Task)
테스트 실패 원인을 네트워크 노이즈와 비즈니스 로직 회귀로 분리 가능하게 만든다.

### A (Action)
1. WireMock confirm 응답 매핑을 안정적인 형태로 정리했다.
2. gateway HTTP client 설정을 조정해 불필요한 프로토콜 노이즈를 줄였다.
3. 동일 시나리오 재실행으로 재발 여부를 확인했다.

참고 파일:
1. `infra/wiremock/mappings/confirm-payment.json`
2. `src/main/java/com/minishop/project/minishop/payment/gateway/TossPaymentGateway.java`

### R (Result)
transport 이슈가 주요 실패 원인이던 구간에서, 로직 회귀와 분리 가능한 수준으로 안정화됐다.

### 개념 해설 (Why this fix)
- 부하 테스트는 SUT(테스트 대상) 외부 노이즈를 최소화해야 신뢰 가능한 결론이 나온다.
- Mock 응답과 클라이언트 전송 계층을 안정화하면 결과 해석이 코드 변경 효과에 집중된다.

---

## Case 5. k6 Setup 예외로 인한 원인 은닉

### S (Situation)
앱 초기화 지연/연결 실패 시 setup에서 `Cannot read property 'data' of undefined`가 발생해 원인 파악이 늦어졌다.

### T (Task)
setup 실패 시 즉시 HTTP 상태/응답 본문을 노출해 장애 분류 시간을 줄인다.

### A (Action)
1. `payment-confirm-contention.js`에 `assertSetup`를 추가했다.
2. 로그인/상품생성/주문/prepare 단계별 검증 실패 시 즉시 명시적 에러를 던지게 했다.

관련 코드:
1. `scripts/k6/payment-confirm-contention.js:38`
2. `scripts/k6/payment-confirm-contention.js:84`
3. `scripts/k6/payment-confirm-contention.js:131`

### R (Result)
setup 단계 실패가 즉시 원인 메시지와 함께 노출되어 초기 기동 문제를 빠르게 분리할 수 있게 됐다.

### 개념 해설 (Why this fix)
- 테스트 스크립트도 관측성(observability)이 필요하다.
- 실패 지점/원인 메시지가 없으면 인프라 문제를 로직 버그로 오판하기 쉽다.

---

## Case 6. TransactionTemplate → 별도 Bean 선언적 @Transactional 리팩토링

### S (Situation)
Case 1에서 PG 호출을 트랜잭션 외부로 분리하기 위해 `TransactionTemplate`(프로그래매틱 트랜잭션)을 도입했다.
결과적으로 경합 문제는 해결됐으나, 다음 가독성 문제가 남았다:
1. `executeInRequiredTx(() -> ...)`, `executeInRequiresNewTx(() -> ...)` 헬퍼가 트랜잭션 의도를 간접적으로 표현
2. 트랜잭션 경계를 파악하려면 헬퍼 메서드 정의까지 따라가야 함
3. `PaymentService`가 320줄로 비대해져 오케스트레이션과 트랜잭션 로직이 혼재

### T (Task)
동작과 트랜잭션 경계를 유지하면서, 선언적 `@Transactional`로 전환해 메서드 시그니처만으로 트랜잭션 의도가 드러나도록 개선한다.

### A (Action)
1. `PaymentConfirmHandler`(package-private)를 `payment/service/` 패키지에 신설했다.
2. confirm 관련 트랜잭션 메서드를 이동하고 선언적 어노테이션을 적용했다:
   - `prepareConfirm()` → `@Transactional`
   - `finalizeConfirmSuccess()` → `@Transactional(propagation = REQUIRES_NEW)`
   - `finalizeConfirmFailure()` → `@Transactional(propagation = REQUIRES_NEW)`
   - `waitForCompletion()` → 트랜잭션 불필요 (polling)
3. `PaymentService.confirmPayment()`는 `confirmHandler`에 위임하는 오케스트레이터로 단순화했다.
4. `TransactionTemplate`, `PlatformTransactionManager`, `EntityManager` 의존을 `PaymentService`에서 제거했다.

관련 코드:
1. `src/main/java/com/minishop/project/minishop/payment/service/PaymentConfirmHandler.java` (신설)
2. `src/main/java/com/minishop/project/minishop/payment/service/PaymentService.java:77` (`confirmPayment` 오케스트레이터)

### R (Result)
1. `PaymentService` 320줄 → 142줄 (56% 감소)
2. 전체 테스트 통과, 동작 변경 없음
3. 트랜잭션 경계가 `@Transactional` 어노테이션으로 즉시 식별 가능

### 왜 이 방식을 선택했는가 (Design Rationale)

**핵심 제약: Spring `@Transactional`은 같은 클래스 내 self-invocation에서 작동하지 않는다.**
Spring AOP는 프록시 기반이므로, `this.prepareConfirm()`처럼 자기 자신을 호출하면 프록시를 경유하지 않아 `@Transactional`이 무시된다.
따라서 메서드별로 트랜잭션을 분리하려면 반드시 (a) 별도 Bean, (b) self-injection, 또는 (c) 프로그래매틱 방식 중 하나를 써야 한다.

**검토한 대안과 트레이드오프:**

| 방식 | 장점 | 단점 | 판단 |
|------|------|------|------|
| **TransactionTemplate** (변경 전) | 한 클래스 완결, 트랜잭션 범위가 코드에 명시 | 헬퍼 보일러플레이트, 비표준 패턴, 람다 중첩으로 가독성 저하 | 기능은 충분하나 유지보수 시 인지 비용 높음 |
| **별도 Bean 분리** (채택) | Spring 표준 관용구, 메서드 시그니처만으로 TX 의도 파악, 단위 테스트 분리 용이 | 클래스 수 증가 (1 → 2), 간접 호출 한 단계 추가 | PaymentService가 이미 비대했으므로 분리가 자연스러움 |
| **Self-injection** (`@Lazy self`) | 클래스 수 유지, 선언적 사용 가능 | `@Lazy` 트릭이 비직관적, 순환 참조 우려, 코드 리뷰 시 혼란 유발 | 비권장 |

**채택 근거:**
- `PaymentService`의 confirm 흐름은 prepareConfirm → PG 호출 → finalize로 3단계가 명확히 분리되어, 별도 Bean으로 추출하는 것이 응집도를 오히려 높인다.
- package-private 클래스로 외부 노출 없이 내부 구현 세부사항을 캡슐화했다.
- `TransactionTemplate`이 적합한 경우(한 메서드 안에서 트랜잭션을 매우 세밀하게 여러 번 열고 닫아야 할 때)와 달리, 이번 구조는 메서드 단위 분리가 자연스러워 선언적 방식이 더 적합하다.

---

## Case 7. 운영 DB `pg_stat_statements` 전/후 확인 (소표본)

### S (Situation)
코드 변경 후 실제 운영(Supabase)에서 슬로우 쿼리가 해소됐는지 확인이 필요했다.  
비교 데이터:
1. Before: `docs/Supabase Query Performance Statements (pbntbykdovzrrauuzwwd).csv`
2. After: `docs/after_slowquery.md` (reset 직후 단기 수집)

### T (Task)
`markAsPaid` 경로에서 발생하던 `orders ... FOR NO KEY UPDATE` 계열의 지연이 실질적으로 감소했는지 확인하고, 표본 한계를 명시한다.

### A (Action)
1. 코드 경로와 SQL 매핑:
   - `PaymentEventListener.handlePaymentCompleted()` -> `orderService.markAsPaid()`
   - `OrderStatusTransitioner.markAsPaidStatus()` -> `OrderRepository.findByIdWithItemsAndLock()` (`PESSIMISTIC_WRITE`)
2. Before/After에서 동일 계열 쿼리 수치를 비교했다.
3. 상위 시간 점유 쿼리가 업무 쿼리인지(대시보드/메타 쿼리인지) 분리해 해석했다.

### R (Result)
`markAsPaid` 핵심 병목으로 보이던 락 쿼리는 큰 폭으로 개선됐다.

| 구분 | calls | mean_time | max_time | total_time |
|------|------:|----------:|---------:|-----------:|
| Before: `orders ... where id=$1 ... for no key update` | 12 | 193.37ms | 2301.78ms | 2320.48ms |
| After: `orders ... left join order_items ... where id=$1 for no key update` | 1 | 1.86ms | 1.86ms | 1.86ms |

추가 관측:
1. `inventories ... where product_id=$1 for no key update`도 After에서 `mean 0.439ms`, `max 1.769ms` 수준.
2. After 상위 1위는 `-- source: dashboard` 쿼리(`prop_total_time 90.35%`)로, 앱 비즈니스 경로와 분리해서 봐야 한다.

### 해석 한계 (중요)
1. After 데이터는 `pg_stat_statements_reset()` 직후라 표본이 작다(`calls`가 1~10대 다수).
2. 수집 시점이 짧다(문서 내 timestamp 기준 약 2분 내외).
3. 따라서 현재 결론은 **“개선 확인(잠정)”**이며, **“완전 해소 확정”**은 장시간 표본으로 재검증이 필요하다.

### 후속 검증 체크리스트
1. 실제 트래픽 30~60분 구간으로 재수집 후 동일 비교 수행
2. `markAsPaid` 계열 쿼리의 `max_time`이 다시 1000ms+로 튀는지 확인
3. 대시보드/메타 쿼리를 분리한 앱 쿼리 기준 보고서 별도 유지

---

## 재현 커맨드 모음

### Scenario A
```powershell
docker compose run --rm k6 run `
  -e BASE_URL=http://app:8080 `
  -e VUS=40 `
  -e ITERATIONS=200 `
  -e STOCK=50 `
  -e ORDER_QTY=1 `
  /scripts/order-lock-contention.js
```

### Scenario B
```powershell
docker compose run --rm k6 run `
  -e BASE_URL=http://app:8080 `
  -e VUS=100 `
  -e ITERATIONS=100 `
  -e PAYMENT_KEY=pk_k6_lock_test `
  /scripts/payment-confirm-contention.js
```

### Scenario C
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

### 로그 점검
```powershell
docker compose logs app --since=10m | Select-String "PG confirm failed|Deadlock found|CannotAcquireLockException|SQLState: 40001|PAY004|PAY002|INVALID_ORDER_STATUS"
```

---

## Case 8. Operational DB Re-check (Updated Snapshot, 2026-03-04)

### S (Situation)
`docs/after_slowquery.md` was refreshed and needed a re-evaluation against the original baseline.

### T (Task)
Re-validate whether slow-query symptoms were actually resolved for the payment/order lock path.

### A (Action)
1. Compared baseline (`docs/Supabase Query Performance Statements (pbntbykdovzrrauuzwwd).csv`) and refreshed snapshot (`docs/after_slowquery.md`).
2. Focused on business-query family (`orders`, `inventories`, `payments`, `order_items`) and lock-related patterns (`FOR NO KEY UPDATE`).
3. Separated dashboard/admin/meta statements from app workload.

### R (Result)
1. Baseline critical lock query:
   - `orders ... for no key update`: `calls=12`, `mean=193.37ms`, `max=2301.78ms`, `total=2320.48ms`.
2. Refreshed snapshot:
   - `orders ... for no key update`: not observed in top list.
   - `inventories ... for no key update`: `calls=6`, `mean=0.439ms`, `max=1.769ms`, `total=2.634ms`.
   - `orders ... status and created_at`: `calls=17`, `mean=0.702ms`, `max=8.685ms`, `total=11.940ms`.
3. Business-query subset summary (from refreshed snapshot):
   - Total time sum (subset): `~25.319ms`
   - Rows with `max_time > 100ms`: `0`
4. Top total-time share is still dashboard/meta query:
   - `-- source: dashboard` aggregate query: `calls=7`, `mean=422.09ms`, `total=2954.66ms`, `prop_total_time=87.32%`.

### Updated Interpretation
- With the refreshed snapshot, lock-contention symptoms in app business queries remain materially improved.
- Current conclusion stays: **resolved trend confirmed**, with the usual caveat that this is still a short-window sample.

### Follow-up Confirmation Rule
1. Re-sample after 30-60 minutes of real traffic.
2. Watch for reappearance of `FOR NO KEY UPDATE` spikes (`max_time >= 1000ms`) in app queries.
3. Keep dashboard/admin/meta excluded in final performance judgment.
