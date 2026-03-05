# 개발 서버 보완 플랜

## Context

무신사 간소화 데모 쇼핑몰 백엔드로서 빠진 부분을 보완한다. 프론트엔드는 별도 프로젝트로 분리 예정이므로, 백엔드 API 서버로서의 완성도에 집중한다. 크게 **인프라 보완**, **Delivery 도메인 신규 추가**, **RetryTask 보상 시스템** 세 축으로 진행한다.

---

## Part A: 인프라 보완 (7개 항목)

### A1. CORS 설정 추가
- **파일**: `SecurityConfig.java`
- **변경**: `http.cors(cors -> cors.configurationSource(...))` 추가
- 개발 환경에서 `localhost:3000` 등 프론트 origin 허용
- `allowedMethods`: GET, POST, PATCH, DELETE, OPTIONS
- `allowedHeaders`: *, `allowCredentials`: true

### A2. Spring Boot Actuator 의존성 추가
- **파일**: `build.gradle`
- **변경**: `implementation 'org.springframework.boot:spring-boot-starter-actuator'` 추가
- SecurityConfig에 이미 `/actuator/health` permitAll 되어 있으므로 엔드포인트만 활성화하면 됨

### A3. Swagger/OpenAPI (springdoc) 추가
- **파일**: `build.gradle` — `implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6'`
- **파일**: `SecurityConfig.java` — `/swagger-ui/**`, `/v3/api-docs/**` permitAll 추가
- Swagger UI (`/swagger-ui/index.html`)로 API 테스트 가능

### A4. H2 Console frameOptions 수정
- **파일**: `SecurityConfig.java`
- **변경**: `.headers(headers -> headers.frameOptions(f -> f.sameOrigin()))` 추가
- H2 Console이 iframe 기반이므로 X-Frame-Options를 SAMEORIGIN으로 변경 필요

### A5. GlobalExceptionHandler catch-all 핸들러 추가
- **파일**: `GlobalExceptionHandler.java`
- **변경**: `@ExceptionHandler(Exception.class)` 메서드 추가
- 예상치 못한 예외 발생 시 HTML 대신 `ApiResponse.error("C001", "Internal server error")` 반환
- `AccessDeniedException` → 403, `AuthenticationException` → 401 분리 처리

### A6. application-dev.properties 생성
- **신규 파일**: `src/main/resources/application-dev.properties`
- H2 DB (create-drop), show-sql=true, 로그 레벨 설정
- Redis excluded (로컬에 Redis 없어도 실행 가능하도록)
- `application.properties`에 `spring.profiles.active=dev` 추가로 기본 프로파일 설정

### A7. 로깅 설정
- **파일**: `application-dev.properties` 내에 포함
- `logging.level.com.minishop=DEBUG`
- `logging.level.org.hibernate.orm.jdbc.bind=TRACE` (SQL 바인딩 파라미터 확인)

---

## Part B: Delivery 도메인 신규 추가

### B0. 설계 개요

**목적**: 결제 완료(PAID) 후 배송 추적 기능 제공. 배송 완료 시 주문이 COMPLETED로 전이.

**상태 흐름**:
```
PREPARING → SHIPPED → IN_TRANSIT → DELIVERED
PREPARING → CANCELED
```

**이벤트 흐름**:
```
Admin creates delivery (PAID order) → Delivery: PREPARING
Admin ships (tracking#) → Delivery: SHIPPED
Admin confirms delivery → Delivery: DELIVERED
  → publishes DeliveryCompletedEvent
  → DeliveryEventListener → orderService.completeOrder()
  → Order: PAID → COMPLETED
```

### B1. Domain 레이어
- **신규**: `delivery/domain/DeliveryStatus.java` — enum (PREPARING, SHIPPED, IN_TRANSIT, DELIVERED, CANCELED)
- **신규**: `delivery/domain/Delivery.java` — 엔티티
  - 필드: id, orderId(Long), userId(Long), status, recipientName, recipientPhone, address, addressDetail, zipCode, carrier, trackingNumber, createdAt, updatedAt, shippedAt, deliveredAt
  - 상태 전이 메서드: `ship()`, `markInTransit()`, `markDelivered()`, `cancel()`
  - orderId에 `unique = true` 제약 (1:1 관계)

### B2. Repository
- **신규**: `delivery/repository/DeliveryRepository.java`
- findByOrderId, findByIdAndUserId, findByOrderIdAndUserId, findByUserId, findByStatus

### B3. Event
- **신규**: `delivery/event/DeliveryCreatedEvent.java`
- **신규**: `delivery/event/DeliveryCompletedEvent.java`
- **신규**: `delivery/event/DeliveryEventListener.java`
  - `DeliveryCompletedEvent` 수신 → `orderService.completeOrder(orderId)` 호출 (비동기, AFTER_COMMIT)
  - **실패 시**: `retryTaskService.register("COMPLETE_ORDER", ...)` 로 DB에 기록

### B4. DTO
- **신규**: `delivery/dto/CreateDeliveryRequest.java` — orderId, recipientName, recipientPhone, address, addressDetail, zipCode
- **신규**: `delivery/dto/ShipDeliveryRequest.java` — carrier, trackingNumber
- **신규**: `delivery/dto/DeliveryResponse.java` — 전체 필드 반환, `from(Delivery)` 팩토리 메서드

### B5. Service
- **신규**: `delivery/service/DeliveryService.java`
- `createDelivery()` — 주문 PAID 상태 검증, 기존 배송 존재 시 멱등 반환
- `shipDelivery()`, `markInTransit()`, `markDelivered()`, `cancelDelivery()`
- `markDelivered()` → `DeliveryCompletedEvent` 발행

### B6. Controller
- **신규**: `delivery/controller/DeliveryController.java` (사용자용)
  - `GET /api/deliveries` — 내 배송 목록
  - `GET /api/deliveries/{id}` — 배송 상세
  - `GET /api/deliveries/order/{orderId}` — 주문별 배송 조회
- **신규**: `delivery/controller/AdminDeliveryController.java` (관리자용)
  - `POST /api/admin/deliveries` — 배송 생성
  - `POST /api/admin/deliveries/{id}/ship` — 발송 처리
  - `POST /api/admin/deliveries/{id}/in-transit` — 배송중
  - `POST /api/admin/deliveries/{id}/deliver` — 배송 완료
  - `POST /api/admin/deliveries/{id}/cancel` — 배송 취소
  - `GET /api/admin/deliveries` — 상태별 조회
  - `GET /api/admin/deliveries/{id}` — 상세

### B7. ErrorCode 추가
- **수정**: `common/exception/ErrorCode.java`
  - `DELIVERY_NOT_FOUND("D001", ...)`, `INVALID_DELIVERY_STATUS("D002", ...)`

---

## Part C: RetryTask 보상 시스템 (outbox 패키지 확장)

### C0. 설계 개요

**목적**: 비동기 이벤트 리스너 실패 시 DB에 재시도 태스크를 기록하고, 단일 스케줄러가 주기적으로 처리하는 일반화된 보상 시스템.

**기존 문제**:
- `PaymentEventListener`: 실패 시 log.error()만 → 유실 위험
- `PaymentCompensationScheduler`: Payment FAILED 상태를 직접 폴링 → 도메인별 스케줄러 난립
- 역방향 보상(롤백) 메커니즘 없음

**해결**:
```
이벤트 리스너 실패 → retryTaskService.register(taskType, payload)로 DB 기록
                  → RetryTaskScheduler가 단일 루프로 처리
                  → taskType별 Handler가 정방향/역방향 보상 수행
```

### C1. Domain 레이어

**RetryTaskStatus.java** (`outbox/domain/`)
```
PENDING    -- 대기 중 (재시도 가능)
COMPLETED  -- 성공
EXHAUSTED  -- 최대 재시도 초과 (수동 개입 필요)
```

**RetryTask.java** (`outbox/domain/`)
```
id (Long, PK)
taskType (String)           -- "COMPLETE_ORDER", "CANCEL_ORDER" 등
payload (String)            -- JSON: {"orderId": 1, "deliveryId": 5}
status (RetryTaskStatus)    -- PENDING / COMPLETED / EXHAUSTED
retryCount (int)            -- 현재 시도 횟수
maxRetries (int)            -- 최대 재시도 (기본 5)
nextRetryAt (Instant)       -- 다음 실행 시각
lastError (String)          -- 마지막 에러 메시지
createdAt (Instant)
updatedAt (Instant)
```

### C2. Repository
**RetryTaskRepository.java** (`outbox/repository/`)
- `findByStatusAndNextRetryAtBefore(PENDING, now)` — 실행 대상 조회
- `findByTaskTypeAndPayloadAndStatus(type, payload, PENDING)` — 중복 등록 방지

### C3. Handler 인터페이스 + 구현체
**RetryTaskHandler.java** (`outbox/handler/`) — 인터페이스
```java
public interface RetryTaskHandler {
    String taskType();           // "COMPLETE_ORDER"
    void handle(String payload); // JSON payload 처리
}
```

**CompleteOrderHandler.java** (`outbox/handler/`)
- taskType: `"COMPLETE_ORDER"`
- payload: `{"orderId": N}`
- 동작: `orderService.completeOrder(orderId)`
- Order가 PAID → 정상 처리
- Order가 CANCELED/REFUNDED → 로그 남기고 COMPLETED 처리 (더 이상 재시도 불필요)

**CancelOrderHandler.java** (`outbox/handler/`)
- taskType: `"CANCEL_ORDER"`
- payload: `{"orderId": N}`
- 동작: `orderService.cancelOrderBySystem(orderId)` (이미 멱등)

### C4. Service
**RetryTaskService.java** (`outbox/service/`)
```java
register(String taskType, String payload)
  → 중복 체크 (같은 taskType+payload+PENDING 존재 시 스킵)
  → RetryTask 생성 (status=PENDING, nextRetryAt=now)

processTask(RetryTask task)
  → handler 디스패치 (taskType → handler 매핑)
  → 성공: status=COMPLETED
  → 실패: retryCount++, nextRetryAt = now + exponentialBackoff(retryCount)
  → retryCount >= maxRetries: status=EXHAUSTED, log.error
```

**Backoff 전략**: `30초 × 2^retryCount` (30s, 60s, 120s, 240s, 480s → 약 15분 내 5회)

### C5. Scheduler
**RetryTaskScheduler.java** (`outbox/scheduler/`)
- `@Scheduled(fixedRate = 30000)` — 30초마다 폴링
- `SELECT * FROM retry_tasks WHERE status=PENDING AND next_retry_at <= now`
- 각 task를 `retryTaskService.processTask(task)` 로 처리

### C6. 기존 코드 변경

**PaymentEventListener.java** — catch 블록 수정:
```java
// Before
catch (Exception e) {
    log.error("Failed to handle PaymentCompletedEvent: ...", e);
}

// After
catch (Exception e) {
    log.error("Failed to handle PaymentCompletedEvent: ...", e);
    retryTaskService.register("COMPLETE_ORDER",
            "{\"orderId\":" + event.getOrderId() + "}");
}
```

동일하게 `handlePaymentFailed`에도 적용:
```java
catch (Exception e) {
    log.error("Failed to handle PaymentFailedEvent: ...", e);
    retryTaskService.register("CANCEL_ORDER",
            "{\"orderId\":" + event.getOrderId() + "}");
}
```

**DeliveryEventListener.java** — 동일 패턴:
```java
catch (Exception e) {
    log.error("Failed to handle DeliveryCompletedEvent: ...", e);
    retryTaskService.register("COMPLETE_ORDER",
            "{\"orderId\":" + event.getOrderId() + "}");
}
```

**PaymentCompensationScheduler.java** — **제거**
- 기존 역할이 RetryTask로 완전히 대체됨
- PaymentEventListener에서 실패 시 바로 retry_task를 등록하므로 별도 폴링 불필요

---

## 수정 대상 파일 요약

| 구분 | 파일 | 변경 유형 |
|------|------|----------|
| A1,A3,A4 | `common/config/SecurityConfig.java` | 수정 (CORS, frameOptions, Swagger 경로) |
| A2,A3 | `build.gradle` | 수정 (actuator, springdoc 의존성) |
| A5 | `common/exception/GlobalExceptionHandler.java` | 수정 (catch-all 핸들러) |
| A6 | `src/main/resources/application.properties` | 수정 (기본 프로파일) |
| A6,A7 | `src/main/resources/application-dev.properties` | **신규** |
| B1 | `delivery/domain/DeliveryStatus.java` | **신규** |
| B1 | `delivery/domain/Delivery.java` | **신규** |
| B2 | `delivery/repository/DeliveryRepository.java` | **신규** |
| B3 | `delivery/event/Delivery*Event.java` (x2) | **신규** |
| B3 | `delivery/event/DeliveryEventListener.java` | **신규** |
| B4 | `delivery/dto/*.java` (x3) | **신규** |
| B5 | `delivery/service/DeliveryService.java` | **신규** |
| B6 | `delivery/controller/*Controller.java` (x2) | **신규** |
| B7 | `common/exception/ErrorCode.java` | 수정 |
| C1 | `outbox/domain/RetryTask.java` | **신규** |
| C1 | `outbox/domain/RetryTaskStatus.java` | **신규** |
| C2 | `outbox/repository/RetryTaskRepository.java` | **신규** |
| C3 | `outbox/handler/RetryTaskHandler.java` | **신규** (인터페이스) |
| C3 | `outbox/handler/CompleteOrderHandler.java` | **신규** |
| C3 | `outbox/handler/CancelOrderHandler.java` | **신규** |
| C4 | `outbox/service/RetryTaskService.java` | **신규** |
| C5 | `outbox/scheduler/RetryTaskScheduler.java` | **신규** |
| C6 | `payment/event/PaymentEventListener.java` | 수정 (catch → register) |
| C6 | `payment/scheduler/PaymentCompensationScheduler.java` | **제거** |

---

## 검증 방법

### 인프라 (Part A)
1. `./gradlew bootRun` — dev 프로파일로 정상 기동 확인
2. `http://localhost:8080/swagger-ui/index.html` 접속 → API 문서 확인
3. `http://localhost:8080/h2-console` 접속 → DB 브라우저 동작 확인
4. `http://localhost:8080/actuator/health` → `{"status":"UP"}` 확인
5. 잘못된 API 호출 시 JSON 에러 응답 확인 (HTML 아닌 `ApiResponse.error`)

### Delivery 도메인 (Part B)
1. `./gradlew test` — 전체 테스트 통과 확인
2. Swagger UI에서 배송 생성 → 발송 → 배송완료 플로우 테스트
3. 배송 완료 후 Order 상태가 COMPLETED로 전이되는지 확인
4. 중복 배송 생성 시 멱등성 확인
5. 잘못된 상태 전이 시 에러 응답 확인

### RetryTask 보상 시스템 (Part C)
1. 이벤트 리스너 실패 시 retry_tasks 테이블에 PENDING 레코드 생성 확인
2. 30초 후 스케줄러가 해당 태스크를 처리하여 COMPLETED로 전이 확인
3. 핸들러 실패 시 retryCount 증가 + nextRetryAt 갱신 (exponential backoff) 확인
4. maxRetries 초과 시 EXHAUSTED 상태 전이 + 로그 확인
5. 중복 register 호출 시 태스크가 중복 생성되지 않음 확인
6. `PaymentCompensationScheduler` 제거 후 기존 결제 실패 보상 플로우 정상 동작 확인

---

## 구현 순서

1. **Part A** (인프라) 먼저 완료 — 기반 환경 정비
2. **Part C** (RetryTask) — 보상 인프라를 먼저 만들어놓기 (C1→C2→C3→C4→C5→C6)
3. **Part B** (Delivery) — B1→B2→B7→B4→B3→B5→B6 순서로 진행
   - B3(DeliveryEventListener)에서 Part C의 retryTaskService 사용
4. 문서 업데이트 (ARCHITECTURE.md, DOMAIN_RULES.md에 Delivery/RetryTask 규칙 추가)
