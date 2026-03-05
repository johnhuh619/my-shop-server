# Mini-Shop 프로젝트 상세 정리 (자소서 배경 데이터)

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **프로젝트명** | Mini-Shop (이커머스 백엔드) |
| **기술 스택** | Spring Boot 4.0.0, Java 21, Gradle, Spring Security, JPA/Hibernate, H2 |
| **외부 연동** | Toss Payments API (PG사 결제) |
| **총 커밋** | 23개 (init → 인증 → 도메인 구현 → 이벤트 비동기화 → Toss 연동) |
| **테스트** | 12개 클래스, 130+ 테스트 메서드, 4500+ 라인 |

---

## 2. 구현한 도메인 (6개)

### A. 인증/사용자 (Auth/User)
- JWT 기반 Stateless 인증 구현 (토큰 발급/검증)
- `JwtAuthenticationFilter` → Spring Security FilterChain에 커스텀 필터 등록
- BCrypt 패스워드 해싱
- Role 기반 접근제어: `CUSTOMER`, `ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`)
- 사용자 등록, 로그인, 비활성화 API

### B. 상품 (Product)
- 상품 CRUD + 상태 관리 (ACTIVE/INACTIVE/DELETED)
- **시간적 분리 원칙**: 상품과 주문 사이 FK 없음 — 과거 데이터(주문)와 현재 데이터(상품) 독립

### C. 재고 (Inventory)
- **Reserve/Release/Confirm 상태 머신** 설계
  - `reserve()`: 주문 시 재고 선점 (available↓, reserved↑)
  - `release()`: 취소/결제실패 시 복구 (available↑, reserved↓)
  - `confirm()`: 완료 시 소비 확정 (reserved↓, total↓)
- **PESSIMISTIC_WRITE 락**: 동시성 환경에서 과잉판매 방지
- 재고 추가(`addStock`) 기능

### D. 주문 (Order/OrderItem)
- **7단계 상태 머신**: CREATED → PAID → COMPLETED / CANCELED / EXPIRED / REFUND_REQUESTED → REFUNDED
- **스냅샷 패턴**: OrderItem에 주문 시점의 상품명/단가/수량 복사 → 상품 가격 변경 시에도 과거 주문 불변
- 주문 생성 시 재고 자동 예약, 취소 시 자동 해제
- **주문 만료 스케줄러**: 60초 주기로 30분 초과 미결제 주문 자동 만료 + 재고 해제

### E. 결제 (Payment) — 핵심 도메인
- **Toss Payments 연동** (Redirect 모델)
  - `preparePayment()`: Payment 생성 + tossOrderId 발급 → 프론트에 전달
  - `confirmPayment()`: PG 승인 API 호출 → 결과에 따라 COMPLETED/FAILED 처리
- **멱등성(Idempotency)**: `(user_id, idempotency_key)` UNIQUE 제약조건
  - 네트워크 재시도 시 동일 결제 중복 생성 방지
  - 동일 키 재요청 → 기존 결제 반환
- **금액 검증**: 클라이언트 전송 금액과 서버 저장 금액 비교 → 위변조 방지
- **이벤트 기반 비동기 처리**:
  - 결제 성공 → `PaymentCompletedEvent` → 비동기로 Order를 PAID 전이
  - 결제 실패 → `PaymentFailedEvent` → 비동기로 재고 해제 (보상 트랜잭션)

### F. 환불 (Refund/RefundItem)
- **부분 환불 지원**: 주문 항목별 수량 지정 환불
- **관리자 승인 워크플로우**: REQUESTED → (관리자 승인/거부) → COMPLETED/REJECTED
- RefundItem에 OrderItem 스냅샷 저장 → 환불 금액 자동 계산
- 중복 환불 방지: 이미 환불된 수량 초과 시 예외
- 승인 시 이벤트 기반 재고 복구 + Order 상태 전이

---

## 3. API 엔드포인트

### Auth (`/api/auth`)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/auth/login` | 로그인 → JWT 발급 |
| POST | `/api/auth/logout` | 로그아웃 |

### Order (`/api/orders`)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/orders` | 주문 생성 (재고 예약) |
| GET | `/api/orders` | 내 주문 목록 |
| GET | `/api/orders/{id}` | 주문 상세 |
| PATCH | `/api/orders/{id}/cancel` | 주문 취소 (재고 해제) |

### Payment (`/api/payments`)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/payments` | 결제 준비 (Header: X-Idempotency-Key) |
| POST | `/api/payments/confirm` | 결제 승인 (Toss PG 호출) |
| GET | `/api/payments/{id}` | 결제 상세 |
| GET | `/api/payments` | 내 결제 목록 |

### Refund (`/api/refunds`)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/refunds` | 환불 요청 |
| GET | `/api/refunds/{id}` | 환불 상세 |
| GET | `/api/refunds` | 내 환불 목록 |

### Admin Refund (`/api/admin/refunds`) — `@PreAuthorize("hasRole('ADMIN')")`
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/admin/refunds?status=` | 상태별 환불 목록 |
| GET | `/api/admin/refunds/{id}` | 환불 상세 |
| POST | `/api/admin/refunds/{id}/approve` | 환불 승인 |
| POST | `/api/admin/refunds/{id}/reject` | 환불 거부 |

### Product (`/api/products`)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/products` | 상품 등록 |
| GET | `/api/products/{id}` | 상품 상세 |
| GET | `/api/products` | 활성 상품 목록 |

### Inventory (`/api/inventories`)
| Method | Endpoint | 설명 |
|--------|----------|------|
| PATCH | `/api/inventories/{productId}/add-stock` | 재고 추가 |
| GET | `/api/inventories/{productId}` | 재고 조회 |

---

## 4. 아키텍처 설계

### 도메인 분리 원칙
- **시간적 분리**: "현재"(Product, Inventory)와 "과거"(Order, Payment, Refund) 도메인 독립
- **Feature 기반 패키지 구조**: `{domain}/{layer}` (controller/service/domain/repository/dto/event)
- **의존성 규칙**: Controller → Service → Domain/Repository, 역방향 참조 금지

### 이벤트 기반 비동기 아키텍처
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`
- 전용 스레드풀 (core 5, max 10, queue 25)
- **장점**: PG 응답 후 Order/Inventory 변경을 비동기 처리 → 응답 시간 단축 + 장애 격리
- **핵심 문제 해결**: `@Transactional(noRollbackFor = BusinessException.class)` 패턴
  - PG 실패 시 Payment를 FAILED로 저장 + 이벤트 발행을 동시에 달성
  - 일반적으로 RuntimeException은 롤백 → AFTER_COMMIT 이벤트 미발행
  - `noRollbackFor`로 트랜잭션 커밋 보장 → 이벤트 정상 발행

### Outbox 패턴 준비
- DB를 Source of Truth로 설계
- 이벤트 발행 구조를 Outbox Table로 전환 가능하도록 설계

---

## 5. 동시성/안정성 문제 해결

| 문제 | 해결 방법 |
|------|-----------|
| 재고 과잉판매 (Race Condition) | `PESSIMISTIC_WRITE` 락 + Fetch Join |
| 결제 중복 생성 (네트워크 재시도) | `(user_id, idempotency_key)` DB UNIQUE 제약조건 |
| 결제 실패 시 재고 미복구 | `PaymentFailedEvent` → 비동기 보상 트랜잭션 |
| @Async 메서드의 Hibernate Session 부재 | Fetch Join으로 원본 스레드에서 연관 데이터 로딩 |
| 미결제 주문의 재고 점유 | 60초 주기 스케줄러로 30분 초과 주문 만료 + 재고 해제 |
| 이벤트 체인 복잡성 | 이벤트 체인 제거 → 단일 이벤트 내부 직접 호출로 전환 |

---

## 6. 테스트 전략

### 테스트 구조
- **단위 테스트 (5개 클래스)**: 도메인 객체의 상태 머신, 비즈니스 규칙, 스냅샷 불변성
- **통합 테스트 (7개 클래스)**: 서비스 계층 + DB + 이벤트 연동 전체 흐름

### 핵심 테스트 패턴

#### 비동기 이벤트 테스트 (Awaitility)
```java
await().atMost(5, SECONDS).untilAsserted(() -> {
    Order paidOrder = orderService.getOrder(orderId, userId);
    assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
});
```
- `Thread.sleep()` 대신 Awaitility로 결정적(deterministic) 비동기 검증

#### 멱등성 스트레스 테스트 (100 동시 스레드)
- 100개 스레드가 동일 `(userId, idempotencyKey)`로 동시 결제 요청
- 결과: DB에 Payment 1건만 생성됨을 검증
- ExecutorService + CountDownLatch + AtomicInteger 활용

#### 동시성 테스트 (재고)
- 20개 스레드가 동시에 재고 예약 → PESSIMISTIC_WRITE 락으로 직렬화 확인
- 재고 10, 각 6개 예약 시도 → 1개만 성공, 9개 INSUFFICIENT_INVENTORY

#### 스냅샷 불변성 검증 (Reflection)
- `OrderItem`의 productName, unitPrice 등에 public setter가 없음을 Reflection으로 증명

#### 테스트용 PG 교체 (TestPaymentGateway)
- `shouldFail` 플래그로 PG 성공/실패 제어
- `@TestConfiguration` + `@Bean @Primary`로 테스트별 독립 주입

### 테스트 커버리지 영역

| 영역 | 검증 내용 |
|------|-----------|
| 결제 멱등성 | 중복 요청 시 동일 결제 반환, 100스레드 동시 요청 |
| 결제 실패 보상 | PG 실패 → 비동기 재고 해제 |
| 주문 만료 | 스케줄러에 의한 자동 만료 + 재고 해제 |
| 환불 수량 검증 | 이미 환불된 수량 초과 시 예외 |
| 재고 동시성 | 20스레드 동시 예약, 과잉판매 방지 |
| 금액 위변조 | 클라이언트 전송 금액과 서버 금액 불일치 검증 |
| 소유권 검증 | 타인의 결제/주문/환불 접근 시 예외 |
| 상태 전이 | 각 도메인의 유효/무효 상태 전이 exhaustive 검증 |

---

## 7. 커밋 히스토리로 본 성장 궤적

```
[Phase 1] 인증 기반 구축
  init → user register → login → token validate → 인증 context 정립

[Phase 2] 핵심 도메인 구현
  Product → Inventory → Order → Payment → Refund + 결제 실패 보상

[Phase 3] 테스트 코드 작성
  Inventory 동시성 → Order 통합 → Payment 멱등성 → Refund 승인 흐름

[Phase 4] 비동기 이벤트 전환 + Toss Payments 연동
  Spring Event 비동기화 → Awaitility 적용 → 이벤트 체인 제거 → Fetch Join 도입
```

---

## 8. 자소서 어필 키워드 정리

| 카테고리 | 키워드 |
|----------|--------|
| **설계** | DDD, 이벤트 기반 아키텍처, 시간적 도메인 분리, 스냅샷 불변성, Outbox 패턴 |
| **동시성** | 비관적 락, 멱등성, DB UNIQUE 제약조건, 보상 트랜잭션 |
| **외부 연동** | Toss Payments PG 통합, RestClient, 금액 위변조 방지 |
| **비동기** | Spring Event, @TransactionalEventListener, @Async, 스레드풀 설정 |
| **트랜잭션** | noRollbackFor 패턴, AFTER_COMMIT 이벤트, 트랜잭션 경계 설계 |
| **테스트** | 100스레드 동시성 테스트, Awaitility 비동기 검증, Reflection 불변성 증명 |
| **보안** | JWT, BCrypt, Role 기반 접근제어, Stateless 인증 |

---

## 9. 프로젝트 핵심 요약

이 프로젝트는 단순 CRUD가 아니라, **실제 이커머스에서 발생하는 동시성/결제/보상 문제를 직접 설계하고 해결한 프로젝트**입니다.

특히 다음 세 가지가 실무 수준의 핵심 문제 해결 경험입니다:
1. **결제 멱등성**: DB UNIQUE 제약조건 + 100스레드 동시성 검증으로 중복 결제 원천 차단
2. **비동기 이벤트 보상 트랜잭션**: PG 실패 시 `noRollbackFor` 패턴으로 실패 상태 저장과 이벤트 발행을 동시 보장
3. **재고 동시성 제어**: PESSIMISTIC_WRITE 락으로 과잉판매 방지, 20스레드 스트레스 테스트로 검증
