# 테스트 규칙 (TEST_RULE.md)

## 1. 테스트 목적

### 1.1 이 프로젝트의 테스트가 검증해야 하는 것

- **정확성(Correctness)**: 비즈니스 로직이 명세대로 동작하는가
- **동시성 안전(Concurrency Safety)**: 동시 요청 시 데이터 정합성이 보장되는가
- **멱등성(Idempotency)**: 중복 요청이 올바르게 처리되는가
- **트랜잭션 일관성(Transaction Consistency)**: 실패 시 롤백이 정상 동작하는가

### 1.2 무의미한 커버리지 테스트 금지

- 커버리지 숫자를 위한 테스트 작성 금지
- 모든 테스트는 실패 가능한 비즈니스 시나리오를 검증해야 함
- "이 테스트가 실패하면 어떤 버그가 있는가?"에 답할 수 없으면 삭제

---

## 2. 레이어별 테스트 범위

### 2.1 Domain 테스트 (순수 단위 테스트)

**목적**: 도메인 불변식(invariant) 검증

**대상**:
- Entity 상태 전이 메서드
- 계산 로직 (예: `OrderItem.getSubtotal()`)
- 검증 규칙 (예: `Inventory.reserve()` 시 available 검증)

**특징**:
- Spring Context 없음
- 순수 Java/JUnit 테스트
- 빠른 실행 속도

**예시**:
```java
@Test
void reserve_성공시_available감소_reserved증가() {
    Inventory inventory = Inventory.create(1L, 10L);
    inventory.reserve(5L);
    assertThat(inventory.getQuantityAvailable()).isEqualTo(5L);
    assertThat(inventory.getQuantityReserved()).isEqualTo(5L);
}

@Test
void reserve_available초과시_예외발생() {
    Inventory inventory = Inventory.create(1L, 10L);
    assertThatThrownBy(() -> inventory.reserve(11L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_INVENTORY);
}
```

### 2.2 Service 테스트 (통합 중심)

**목적**: 비즈니스 플로우 전체 검증

**대상**:
- 트랜잭션 경계
- 서비스 간 협력
- 실패 시 보상 로직

**특징**:
- `@SpringBootTest` 또는 `@DataJpaTest`
- 실제 DB 연동 (H2)
- `@Transactional`로 테스트 후 롤백

**예시**:
```java
@Test
void createOrder_성공시_재고예약됨() {
    // Given
    Product product = productService.createProduct(...);
    inventoryService.addStock(product.getId(), 10L);

    // When
    Order order = orderService.createOrder(userId, List.of(
        new OrderItemRequest(product.getId(), 5L)
    ));

    // Then
    Inventory inventory = inventoryService.getByProductId(product.getId());
    assertThat(inventory.getQuantityReserved()).isEqualTo(5L);
}
```

### 2.3 Controller 테스트 (최소 스모크 테스트)

**목적**: API 계약 검증

**대상**:
- HTTP 상태 코드
- 요청/응답 JSON 형식
- 인증/인가 동작

**범위 (핵심 경로만)**:
- 200 OK: 정상 응답
- 400 Bad Request: 잘못된 입력
- 401 Unauthorized: 인증 실패
- 404 Not Found: 리소스 없음

**특징**:
- `@WebMvcTest`
- MockMvc 사용
- Service는 Mock 처리

**예시**:
```java
@Test
void createOrder_정상요청_200반환() throws Exception {
    mockMvc.perform(post("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .content(orderRequest))
        .andExpect(status().isOk());
}
```

### 2.4 동시성 & 멱등성 테스트 (필수)

**목적**: 레이스 컨디션 방지 검증

**특징**:
- `ExecutorService`로 병렬 실행
- `CountDownLatch`로 동기화
- `@SpringBootTest` (실제 DB 락 테스트)

**예시**:
```java
@Test
void 동시_재고예약_하나만_성공() throws InterruptedException {
    // Given: 재고 10개
    inventoryService.addStock(productId, 10L);

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger();

    // When: 10개 스레드가 동시에 6개씩 예약 시도
    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                inventoryService.reserve(productId, 6L);
                successCount.incrementAndGet();
            } catch (BusinessException e) {
                // 재고 부족으로 실패
            } finally {
                latch.countDown();
            }
        });
    }
    latch.await();

    // Then: 하나만 성공
    assertThat(successCount.get()).isEqualTo(1);
}
```

---

## 3. 테스트하지 않을 것

### 3.1 Framework 동작
- Spring DI가 동작하는지
- `@Autowired`가 주입되는지
- `@Transactional`이 롤백되는지

### 3.2 JPA 구현 세부사항
- `CascadeType.ALL`이 동작하는지
- `FetchType.LAZY`가 N+1을 발생시키는지
- 쿼리가 예상대로 생성되는지

### 3.3 단순 getter/setter
```java
// 이런 테스트 금지
@Test
void getId_returnsId() {
    Order order = new Order();
    order.setId(1L);
    assertThat(order.getId()).isEqualTo(1L);
}
```

### 3.4 Spring 설정 정확성
- `@Configuration` 클래스가 빈을 생성하는지
- `application.properties` 값이 주입되는지

### 3.5 외부 라이브러리 동작
- Lombok `@Builder`가 동작하는지
- Jackson이 JSON을 직렬화하는지

---

## 4. 도메인별 필수 테스트

### 4.1 Inventory 도메인

#### 동시성 테스트 (CRITICAL)
| 시나리오 | 설명 | 예상 결과 |
|---------|------|----------|
| 동시 reserve | N개 스레드가 동시에 `reserve()` 호출 | PESSIMISTIC_WRITE로 순차 처리 |
| 초과 판매 방지 | available=10, 동시에 6개씩 예약 | 하나만 성공, 나머지 `INSUFFICIENT_INVENTORY` |

#### 상태 불변식 테스트
| 시나리오 | 설명 | 예상 결과 |
|---------|------|----------|
| available >= 0 | reserve 후 available이 음수가 되지 않음 | 예외 발생 |
| reserved >= 0 | release 후 reserved가 음수가 되지 않음 | 예외 발생 |
| reserve → release | reserve한 만큼만 release 가능 | reserved 초과 시 예외 |
| reserve → confirm | reserve한 만큼만 confirm 가능 | reserved 초과 시 예외 |

### 4.2 Order 도메인

#### 상태 전이 테스트 (CRITICAL)
| 현재 상태 | 이벤트 | 다음 상태 | 테스트 |
|----------|--------|----------|--------|
| CREATED | `markAsPaid()` | PAID | 성공 |
| CREATED | `cancel()` | CANCELED | 성공 |
| CREATED | `expire()` | EXPIRED | 성공 |
| PAID | `markAsPaid()` | - | 예외 |
| PAID | `cancel()` | - | 예외 |
| PAID | `requestRefund()` | REFUND_REQUESTED | 성공 |
| PAID | `complete()` | COMPLETED | 성공 |
| REFUND_REQUESTED | `markAsRefunded()` | REFUNDED | 성공 |

#### 스냅샷 불변 테스트
| 시나리오 | 설명 | 예상 결과 |
|---------|------|----------|
| OrderItem 생성 후 변경 | `OrderItem.unitPrice` 변경 시도 | setter 없음 또는 예외 |
| Product 가격 변경 | Product 가격 변경 후 OrderItem 확인 | OrderItem 가격 불변 |

#### 소유권 검증 테스트
| 시나리오 | 설명 | 예상 결과 |
|---------|------|----------|
| 타인 주문 조회 | 다른 userId로 조회 | `ORDER_NOT_FOUND` |
| 타인 주문 취소 | 다른 userId로 취소 | `ORDER_NOT_FOUND` |

### 4.3 Payment 도메인

#### 멱등성 테스트 (CRITICAL)
| 시나리오 | 설명 | 예상 결과 |
|---------|------|----------|
| 동일 key 재요청 | 같은 (userId, idempotencyKey) | 기존 Payment 반환 |
| 동일 key + 다른 orderId | 같은 key로 다른 주문 결제 | `DUPLICATE_PAYMENT` 예외 |
| 다른 key 요청 | 같은 userId, 다른 key | 새 Payment 생성 |

#### 실패 보상 테스트
| 시나리오 | 설명 | 예상 결과 |
|---------|------|----------|
| 결제 실패 | 외부 결제 실패 시 | 재고 release 호출됨 |
| 결제 실패 후 재고 | 결제 실패 후 재고 확인 | reserved=0, available=원복 |

#### 상태 전이 테스트
| 현재 상태 | 이벤트 | 다음 상태 |
|----------|--------|----------|
| REQUESTED | `markAsCompleted()` | COMPLETED |
| REQUESTED | `markAsFailed()` | FAILED |
| COMPLETED | `markAsFailed()` | 예외 |

### 4.4 Refund 도메인

#### 전제조건 테스트
| 시나리오 | 설명 | 예상 결과 |
|---------|------|----------|
| CREATED 주문 환불 | 결제 전 주문 환불 시도 | `REFUND_NOT_ALLOWED` |
| CANCELED 주문 환불 | 취소된 주문 환불 시도 | `REFUND_NOT_ALLOWED` |
| PAID 주문 환불 | 결제 완료 주문 환불 | 성공 |

#### 금액 검증 테스트
| 시나리오 | 설명 | 예상 결과 |
|---------|------|----------|
| 전액 환불 | amount = paymentAmount | 성공 |
| 부분 환불 | amount < paymentAmount | 성공 |
| 초과 환불 | amount > paymentAmount | `REFUND_AMOUNT_EXCEEDED` |
| 0원 환불 | amount = 0 | `INVALID_REFUND_AMOUNT` |

#### 재고 복구 테스트
| 시나리오 | 설명 | 예상 결과 |
|---------|------|----------|
| 환불 성공 | 환불 완료 후 재고 | available 복구됨 |
| 환불 실패 | 환불 실패 후 재고 | available 변화 없음 |

---

## 5. 테스트 네이밍 컨벤션

### 5.1 형식
```
[테스트대상]_[시나리오]_[예상결과]
```

### 5.2 예시
```java
// Good
void reserve_재고충분_성공()
void reserve_재고부족_예외발생()
void createOrder_상품없음_예외발생()
void processPayment_중복키_기존결제반환()

// Bad
void testReserve()
void test1()
void reserveTest()
```

---

## 6. 테스트 우선순위

### P0 (필수 - PR 머지 차단)
- 동시성 테스트 (Inventory 동시 예약)
- 멱등성 테스트 (Payment 중복 요청)
- 상태 전이 테스트 (Order, Payment)
- 실패 보상 테스트 (결제 실패 → 재고 해제)

### P1 (권장 - 코드 리뷰에서 확인)
- 도메인 불변식 테스트
- 서비스 통합 테스트
- 소유권 검증 테스트

### P2 (선택 - 리소스 여유 시)
- Controller 스모크 테스트
- 엣지 케이스 테스트
- 성능 테스트

---

## 7. 테스트 환경 설정

### 7.1 H2 In-Memory Database
```properties
# application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL
spring.jpa.hibernate.ddl-auto=create-drop
```

### 7.2 테스트 격리
- 각 테스트는 독립적으로 실행 가능해야 함
- `@Transactional`로 테스트 후 롤백
- `@DirtiesContext`는 최소화 (느림)

### 7.3 동시성 테스트 주의사항
- `@Transactional` 사용 시 실제 락 테스트 불가
- 동시성 테스트는 `@Transactional` 제거
- `@AfterEach`에서 수동 정리

---

## 8. 테스트 파일 구조

```
src/test/java/com/minishop/project/minishop/
├── inventory/
│   ├── domain/
│   │   └── InventoryTest.java           # 도메인 불변식
│   └── service/
│       └── InventoryConcurrencyTest.java # 동시성 테스트
├── order/
│   ├── domain/
│   │   ├── OrderTest.java               # 상태 전이
│   │   └── OrderItemTest.java           # 스냅샷 불변
│   └── service/
│       └── OrderServiceTest.java        # 통합 플로우
├── payment/
│   ├── domain/
│   │   └── PaymentTest.java             # 상태 전이
│   └── service/
│       ├── PaymentServiceTest.java      # 통합 플로우
│       └── PaymentIdempotencyTest.java  # 멱등성 테스트
├── refund/
│   ├── domain/
│   │   └── RefundTest.java              # 금액 검증
│   └── service/
│       └── RefundServiceTest.java       # 환불 플로우
└── integration/
    ├── OrderPaymentFlowTest.java        # 주문→결제 통합
    └── OrderRefundFlowTest.java         # 결제→환불 통합
```
