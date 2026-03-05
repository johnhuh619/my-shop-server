# Mini-Shop

결제 정합성, 재고 동시성, 장애 보상에 집중한 이커머스 풀스택 애플리케이션.

> **Live Demo**
> - Frontend: [Vercel](https://mini-shop-frontend.vercel.app) (React + TypeScript)
> - Backend API: [GCP Cloud Run](https://minishop-asia-northeast1.run.app) (Spring Boot)
>
> *Toss Payments 테스트 모드로 실제 결제 흐름을 체험할 수 있습니다.*

---

## Tech Stack

| 구분 | 기술 |
|---|---|
| **Backend** | Java 21, Spring Boot 4.0.0, Spring Security, JPA/Hibernate |
| **Frontend** | React 19, TypeScript, Vite, TanStack Query, Seed Design, Tailwind CSS |
| **Database** | PostgreSQL 16 (Supabase), Redis 7 (Upstash) |
| **External** | Toss Payments API |
| **Infra** | Docker Compose, GCP Cloud Run, Cloud Build, Vercel |
| **Testing** | JUnit 5, Awaitility, k6 (부하 테스트), WireMock (PG Mock) |

---

## 핵심 설계 원칙

- **결제는 단 한 번만** — `(user_id, idempotency_key)` UNIQUE 제약으로 중복 결제 차단
- **과거 데이터는 불변** — OrderItem에 주문 시점 상품 스냅샷 저장, 상품 가격 변경에 영향받지 않음
- **재고는 예약으로 관리** — reserve/release/confirm 상태 머신으로 동시성 제어
- **느린 작업은 비동기** — 결제 완료 후 주문/배송 처리를 이벤트 기반으로 분리
- **장애가 나도 복구 가능** — RetryTask 기반 보상 처리로 최종적 일관성 보장

> 상세: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | [docs/DOMAIN_RULES.md](docs/DOMAIN_RULES.md)

---

## 아키텍처 개요

```
[Client]
   │
   ▼
[Spring Security — JWT 인증]
   │
   ▼
┌─────────────────────────────────────────────────────────┐
│                     API Layer                           │
│  Order  │  Payment  │  Refund  │  Delivery  │  Product  │
└────┬────┴─────┬─────┴────┬────┴─────┬──────┴─────┬────┘
     │          │          │          │            │
     ▼          ▼          ▼          ▼            ▼
┌─────────────────────────────────────────────────────────┐
│                   Domain / Service                      │
│                                                         │
│  주문 생성 ──→ 재고 예약 (PESSIMISTIC_WRITE)             │
│  결제 확인 ──→ PG 승인 ──→ PaymentCompletedEvent        │
│                              │                          │
│              ┌───────────────┼───────────────┐          │
│              ▼               ▼               ▼          │
│         Order PAID    Inventory confirm  Delivery 생성  │
│              │          (멱등성 로그)     (멱등 생성)     │
│              ▼                                          │
│      DeliveryCompletedEvent ──→ Order COMPLETED         │
│                                                         │
│  실패 시: RetryTask 등록 → 스케줄러 재시도 (exp backoff) │
└─────────────────────────────────────────────────────────┘
     │                          │
     ▼                          ▼
 PostgreSQL                   Redis
 (Source of Truth)         (Token Blacklist)
```

---

## 주요 트러블슈팅

이 프로젝트에서 가장 깊이 있게 고민한 문제 3가지입니다.

### 1. Order 락 경합 구조 재설계 — [분석]

운영 DB에서 `orders FOR UPDATE` 쿼리가 **max 2,301ms**를 기록. 단순 쿼리 튜닝이 아니라 Order/Inventory 이중 비관락, 커넥션 풀, 스레드 풀, 스케줄러까지 시스템 레벨로 원인을 분해하고, 멱등성 로그 + 트랜잭션 분리로 **1.86ms**로 완화.

> 상세: [ORDER_LOCK_OPTIMIZATION.md](docs/troubleshooting/ORDER_LOCK_OPTIMIZATION.md)

### 2. JPA L1 캐시 stale read 해결 — [디버깅]

`SELECT FOR UPDATE`를 사용해도 같은 영속성 컨텍스트에서 **stale 엔티티가 반환**되는 비자명한 버그 발견. JPA 동일성 보장과 비관락의 상호작용을 타임라인으로 분해하고, 트랜잭션 경계 재설계로 상태 전이 정합성 이슈 제거.

> 상세: [JPA_L1_CACHE_TROUBLESHOOTING.md](docs/troubleshooting/JPA_L1_CACHE_TROUBLESHOOTING.md)

### 3. 결제-배송 비동기 설계 결정 — [판단]

PG 승인이라는 되돌릴 수 없는 외부 부수효과와 실패 가능한 내부 작업을 하나의 트랜잭션에 묶는 위험을 장애 시나리오로 모델링. 비동기 이벤트 + RetryTask 보상으로 장애 전파를 구조적으로 차단.

> 상세: [DELIVERY_AUTO_CREATION_DESIGN.md](docs/troubleshooting/DELIVERY_AUTO_CREATION_DESIGN.md)

> 전체 트러블슈팅 순위와 요약: [TROUBLESHOOTING_RESUME_SUMMARY.md](docs/troubleshooting/TROUBLESHOOTING_RESUME_SUMMARY.md)

---

## 실행 방법

### Docker Compose (백엔드 전체)

```bash
docker compose up -d
# app: http://localhost:8080
# PostgreSQL, Redis, WireMock(PG Mock) 자동 기동
```

### 프론트엔드

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173
```

### 테스트

```bash
# 백엔드 전체 테스트
./gradlew test

# 특정 테스트 클래스
./gradlew test --tests "PaymentServiceTest"

# k6 부하 테스트 (Docker Compose 기동 상태에서)
docker compose run --rm k6 run \
  -e BASE_URL=http://app:8080 \
  -e VUS=100 -e ITERATIONS=100 \
  /scripts/payment-confirm-contention.js
```

---

## 프로젝트 구조

```
mini-shop/
├── src/main/java/.../minishop/
│   ├── auth/                    # JWT 인증/인가
│   ├── user/                    # 사용자 도메인
│   ├── product/                 # 상품 도메인
│   ├── inventory/               # 재고 도메인 (reserve/release/confirm)
│   ├── order/                   # 주문 도메인 (7단계 상태 머신)
│   ├── payment/                 # 결제 도메인 (Toss Payments 연동)
│   ├── refund/                  # 환불 도메인 (부분 환불)
│   ├── delivery/                # 배송 도메인
│   └── common/                  # 공통 설정, 예외, 이벤트
├── frontend/
│   └── src/
│       ├── features/            # feature 기반 구조 (auth, order, payment, ...)
│       ├── shared/              # 공통 API client, 인증, UI 컴포넌트
│       └── app/                 # 라우터, 레이아웃, 프로바이더
├── scripts/k6/                  # 부하 테스트 시나리오
├── infra/wiremock/              # PG Mock 매핑
├── docs/                        # 설계/트러블슈팅 문서
└── docker-compose.yml
```

> 패키지 규칙 상세: [docs/PACKAGE_RULES.md](docs/PACKAGE_RULES.md)

---

## API 요약

| 도메인 | 주요 엔드포인트 | 설명 |
|---|---|---|
| Auth | `POST /api/auth/login` | JWT 발급 |
| Product | `GET /api/products` | 상품 목록 (pg_trgm 검색) |
| Order | `POST /api/orders` | 주문 생성 (재고 예약) |
| Payment | `POST /api/payments` | 결제 준비 (멱등성 키) |
| Payment | `POST /api/payments/confirm` | 결제 확인 (PG 승인) |
| Refund | `POST /api/refunds` | 환불 요청 (부분 환불) |
| Delivery | `GET /api/deliveries` | 배송 조회 |
| Admin | `POST /api/admin/refunds/{id}/approve` | 환불 승인 |

> 전체 API 명세: [docs/API_SPEC.md](docs/API_SPEC.md)

---

## 테스트

14개 테스트 클래스, 130+ 테스트 메서드.

| 영역 | 내용 |
|---|---|
| 동시성 | 20~100 스레드 재고 예약, 멱등성 검증 |
| 비동기 이벤트 | Awaitility 기반 결제→주문 상태 전이 검증 |
| 부하 테스트 | k6 시나리오 3종 (경합, 데드락, 결제 confirm) |
| PG 장애 | TestPaymentGateway + WireMock으로 실패 시나리오 검증 |

---

## 배포 구성

```
┌─ Vercel ──────────┐     ┌─ GCP Cloud Run ─────────────┐
│  React SPA        │────▶│  Spring Boot Docker          │
│  (Frontend)       │     │  1Gi, min=0, max=2           │
└───────────────────┘     │  Cloud Build CI/CD           │
                          └──────┬──────────┬────────────┘
                                 │          │
                                 ▼          ▼
                          ┌──────────┐ ┌───────────┐
                          │ Supabase │ │  Upstash  │
                          │ Postgres │ │   Redis   │
                          └──────────┘ └───────────┘
```

- Secret 관리: GCP Secret Manager
- CI/CD: Cloud Build → Cloud Run 자동 배포

> 배포 가이드: [docs/DEPLOY_CLOUD_RUN.md](docs/DEPLOY_CLOUD_RUN.md)

---

## 문서

### 설계
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) — 시스템 아키텍처
- [DOMAIN_RULES.md](docs/DOMAIN_RULES.md) — 도메인 불변식
- [PACKAGE_RULES.md](docs/PACKAGE_RULES.md) — 패키지 구조 규칙
- [EVENT_CHAIN_PATTERNS.md](docs/EVENT_CHAIN_PATTERNS.md) — 이벤트 체인 패턴
- [RETRY_TASK_SYSTEM.md](docs/RETRY_TASK_SYSTEM.md) — 재시도 태스크 시스템

### 트러블슈팅
- [TROUBLESHOOTING_RESUME_SUMMARY.md](docs/troubleshooting/TROUBLESHOOTING_RESUME_SUMMARY.md) — 전체 요약 (순위별)
- [ORDER_LOCK_OPTIMIZATION.md](docs/troubleshooting/ORDER_LOCK_OPTIMIZATION.md) — Order 락 경합 재설계
- [JPA_L1_CACHE_TROUBLESHOOTING.md](docs/troubleshooting/JPA_L1_CACHE_TROUBLESHOOTING.md) — JPA L1 캐시
- [K6_TEST_TROUBLESHOOTING_STAR.md](docs/troubleshooting/K6_TEST_TROUBLESHOOTING_STAR.md) — k6 부하 테스트
- [DELIVERY_AUTO_CREATION_DESIGN.md](docs/troubleshooting/DELIVERY_AUTO_CREATION_DESIGN.md) — 배송 비동기 설계
- [PAYMENT_TEST_TROUBLESHOOTING.md](docs/troubleshooting/PAYMENT_TEST_TROUBLESHOOTING.md) — 결제 동시성
- [ORDER_LIST_N_PLUS_1_FACADE.md](docs/troubleshooting/ORDER_LIST_N_PLUS_1_FACADE.md) — N+1 최적화

### 운영
- [API_SPEC.md](docs/API_SPEC.md) — API 명세
- [DEPLOY_CLOUD_RUN.md](docs/DEPLOY_CLOUD_RUN.md) — 배포 가이드
- [LOAD_TEST.md](docs/LOAD_TEST.md) — 부하 테스트 가이드
