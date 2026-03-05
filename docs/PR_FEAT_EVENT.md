# Title
feat: 결제/주문/배송 이벤트 체인 고도화 + 인증 토큰 전략 강화 + 프론트 웹앱 구축

## Summary
- Base: `main`
- Head: `feat/event`
- Range: `main..feat/event`
- Commits: `28`
- Files changed: `197`
- Diff: `+17,192 / -527`

## Why
- 결제 확정 경합, PG 실패, 후속 상태 전이 실패에 대한 복원력이 필요했습니다.
- 단일 결제 API로는 Toss 결제창 연동 요구사항을 만족하기 어려웠습니다.
- 로그아웃/refresh token 전략이 없어 세션 통제가 제한적이었습니다.
- 주문 상세에서 환불/배송 상태가 부족해 사용자 경험이 떨어졌습니다.
- 운영 환경 전환을 위한 프로필/보안/CORS/문서/프론트 체계화가 필요했습니다.

## What Changed

### 1. Payment/Order/Reliability
- 결제 API를 `prepare -> confirm` 2단계로 분리했습니다.
  - `POST /api/payments` (prepare)
  - `POST /api/payments/confirm` (confirm)
- `PaymentConfirmHandler`를 도입해 동시성 제어를 강화했습니다.
  - `REQUESTED -> PROCESSING` 전이 시 row lock 적용
  - 성공/실패 finalize를 별도 트랜잭션(`REQUIRES_NEW`)으로 처리
  - 완료 대기 로직 추가
- 결제 이벤트 처리 고도화:
  - 결제 완료 시 주문 `PAID` 전이
  - 주문 `PAID` 후 배송 자동 생성
  - 실패 시 retry task 등록(outbox)

### 2. Outbox Retry Task
- 신규 구성:
  - `RetryTask`, `RetryTaskService`, `RetryTaskScheduler`
  - handler: `MARK_AS_PAID`, `CREATE_DELIVERY`, `CANCEL_ORDER`, `COMPLETE_ORDER`
- 중복 등록 방지 + backoff 재시도 + EXHAUSTED 상태 관리 추가

### 3. Delivery Domain + Order Query Enrichment
- 배송 도메인(엔티티/서비스/컨트롤러/리포지토리) 신규 추가
  - 사용자 조회: `/api/deliveries`, `/api/deliveries/{id}`, `/api/deliveries/order/{orderId}`
  - 관리자 처리: `/api/admin/deliveries/**`
- 주문 엔티티에 배송지 스냅샷 컬럼 추가
  - `recipientName`, `recipientPhone`, `address`, `addressDetail`, `zipCode`
- 주문 응답 확장:
  - `deliveryStatus`
  - `items[].refunded`
- `OrderQueryService`로 주문/환불/배송 상태 배치 조합 조회 추가

### 4. Auth/Security
- 로그인 응답에 `refreshToken` 추가
- 신규 API:
  - `POST /api/auth/refresh`
- 로그아웃 실구현:
  - access/refresh token 블랙리스트 처리
- Redis 기반 `TokenBlacklistService` 도입
- JWT에 `jti`, `tokenType(access|refresh)` 도입
- `SecurityConfig` 정비:
  - CORS 외부화(`cors.allowed-origins`)
  - 공개 경로/Swagger/헬스체크/테스트 페이지 정책 정리

### 5. Product/Admin API Restructure
- 공개 상품 API:
  - `GET /api/products` -> 페이지네이션 + keyword 검색
  - `GET /api/products/{id}` -> `quantityAvailable` 포함
- 관리자 API 분리:
  - `/api/admin/products`
  - `/api/admin/inventories`
  - `/api/admin/orders/{id}/complete`
  - `/api/admin/deliveries/**`

### 6. Frontend (React/Vite)
- `frontend/` 앱 구성 및 주요 화면 구현
  - auth, product, cart, order, payment, refund, delivery, mypage, admin
- 라우팅/인증 가드:
  - `RequireAuth`, `RequireAdmin`
- 세션/토큰 처리:
  - session store
  - axios interceptor + 자동 refresh 재시도

### 7. Build/Config
- `build.gradle`에 의존성 추가:
  - validation, redis, actuator, springdoc, postgresql, awaitility
- 환경 설정 분리:
  - `application-dev.properties`
  - `application-docker.properties`
  - `application-prod.properties`

## Breaking Changes
| Area | Before | After | Action Required |
|---|---|---|---|
| Payment API | `POST /api/payments` 단일 처리 | prepare/confirm 2단계 | 클라이언트 결제 플로우 수정 |
| Order Create Request | items만 전달 | 배송지 필드 필수 추가 | 요청 바디 스키마 갱신 |
| Product List Response | 배열 응답 | `PageResponse` 구조 | 리스트 파싱 로직 변경 |
| Login Response | accessToken 중심 | refreshToken 포함 | 세션 저장 로직 반영 |

### Updated Request Example: `POST /api/orders`
```json
{
  "items": [{ "productId": 1, "quantity": 2 }],
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678",
  "address": "서울시 강남구",
  "addressDetail": "101동 1001호",
  "zipCode": "06000"
}
```

## Impact Scope
- Backend (`src/main/java`): 93 files
- Backend tests (`src/test/java`): 12 files
- Frontend: 80 files
- Resources/config: 5 files

## Test Evidence
Executed:
```bash
.\gradlew.bat test --tests "com.minishop.project.minishop.payment.service.PaymentServiceTest" --tests "com.minishop.project.minishop.order.service.OrderServiceTest" --tests "com.minishop.project.minishop.refund.service.RefundServiceTest"
```

Result:
- `PaymentServiceTest`: 24 passed, 0 failed
- `OrderServiceTest`: 17 passed, 0 failed
- `RefundServiceTest`: 18 passed, 0 failed

## Deployment Notes
- Required env:
  - `JWT_SECRET`
  - `DB_URL`, `DB_USER`, `DB_PASSWORD`
  - `REDIS_URL`
  - `TOSS_SECRET_KEY`
- 운영 DB는 신규 컬럼/테이블(`retry_tasks` 등) 반영 전 사전 점검이 필요합니다.

## Rollback Notes
- API 계약이 변경되었으므로(orders/products/payments/auth) 서버/클라이언트 동시 롤백 전략이 필요합니다.
- outbox 관련 신규 스키마는 롤백 후에도 잔존 가능하므로 하위 호환성을 확인해야 합니다.

## Reviewer Checklist
- [ ] 결제 confirm 경합 제어(row lock + 상태 전이) 검토
- [ ] outbox retry 중복 방지 및 실패 처리 검토
- [ ] API 스키마 변경에 대한 클라이언트 영향 확인
- [ ] Redis fail-open 정책의 보안 적합성 확인
- [ ] 프론트 refresh/가드 동작 확인
