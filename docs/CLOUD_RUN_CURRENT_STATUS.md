# Cloud Run Current Status

`mini-shop` 백엔드의 Cloud Run 배포/운영 상태를 정리한 문서다.
기준 시점은 2026-03-18 이다.

## 현재 적용 상태

- Cloud Build 실행 계정은 `minishop-builder@<PROJECT_ID>.iam.gserviceaccount.com` 이다.
- Cloud Run 런타임 계정은 `minishop-runner@<PROJECT_ID>.iam.gserviceaccount.com` 이다.
- 배포는 [cloudbuild.yaml](/C:/2025proj/mini-shop/cloudbuild.yaml) 기준으로 Artifact Registry 이미지 빌드 후 Cloud Run `minishop` 서비스에 반영된다.
- prod 에서는 앱 내부 `@Scheduled` 작업을 끄고, Cloud Scheduler 가 내부 job endpoint 를 1분마다 호출한다.
- 내부 job 인증은 `X-Internal-Job-Key` 헤더 기반이다.
- `/internal/**` 경로는 기존 rate limit 대상이 아니므로 Scheduler 호출과 충돌하지 않는다.

## 현재 운영 방식

### 내부 job endpoint

- 주문 만료 처리:
  - `POST /internal/jobs/orders/expire?limit=100`
  - job name: `minishop-expire-orders`
- retry batch 처리:
  - `POST /internal/jobs/retry-tasks/process?limit=100`
  - job name: `minishop-process-retry-tasks`

### prod 설정

- [application-prod.properties](/C:/2025proj/mini-shop/src/main/resources/application-prod.properties)
  - `internal.jobs.scheduler.enabled=false`
  - `internal.jobs.auth-key=${INTERNAL_JOBS_AUTH_KEY}`
  - `internal.jobs.order-expiration.batch-size=100`
  - `internal.jobs.retry.batch-size=100`

## 서비스 계정과 권한

### 런타임 서비스 계정

- `minishop-runner`
- 역할:
  - `roles/secretmanager.secretAccessor`

### 빌드 서비스 계정

- `minishop-builder`
- 역할:
  - `roles/run.admin`
  - `roles/artifactregistry.writer`
  - `roles/logging.logWriter`
- 추가 권한:
  - `roles/iam.serviceAccountUser` on `minishop-runner`
  - `roles/storage.objectViewer` on Cloud Build source bucket

## 필수 secret

현재 `cloudbuild.yaml` 기준 주입 secret:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `REDIS_URL`
- `JWT_SECRET`
- `TOSS_SECRET_KEY`
- `INTERNAL_JOBS_AUTH_KEY`

주의:

- `INTERNAL_JOBS_AUTH_KEY` 는 개행 없이 Secret Manager 에 저장해야 한다.
- PowerShell 에서는 `Set-Content -NoNewline` 방식으로 업로드하는 편이 안전하다.

## 반영 완료

- 내부 job endpoint 추가
- `X-Internal-Job-Key` 인증 필터 추가
- prod 에서 인앱 scheduler 비활성화
- Cloud Scheduler 로 주문 만료 / retry batch 외부화
- `INTERNAL_JOBS_AUTH_KEY` Secret Manager 주입 반영
- Cloud Build 전용 계정 `minishop-builder` 분리
- Cloud Run 런타임 계정 `minishop-runner` 유지
- 관련 테스트 통과
- Cloud Scheduler 실제 호출 로그 확인 완료

## 현재 확인된 정상 동작

- `/actuator/health` 공개 접근 정상
- Cloud Scheduler 에서 아래 endpoint 들이 `POST 200` 으로 호출됨
  - `/internal/jobs/orders/expire?limit=100`
  - `/internal/jobs/retry-tasks/process?limit=100`

## 아직 남겨둔 항목

- 내부 job 인증을 OIDC / IAM 기반으로 고도화
- retry 를 Cloud Tasks 기반 개별 dispatch 로 전환
- `compute default service account` 의존 여부 최종 정리
- Cloud Run 최적화 추가 작업
  - `ddl-auto=update` 제거
  - startup initializer 운영 정리
  - graceful shutdown 설정 보강

## 관련 파일

- [cloudbuild.yaml](/C:/2025proj/mini-shop/cloudbuild.yaml)
- [application-prod.properties](/C:/2025proj/mini-shop/src/main/resources/application-prod.properties)
- [SecurityConfig.java](/C:/2025proj/mini-shop/src/main/java/com/minishop/project/minishop/common/config/SecurityConfig.java)
- [InternalJobAuthFilter.java](/C:/2025proj/mini-shop/src/main/java/com/minishop/project/minishop/common/filter/InternalJobAuthFilter.java)
- [InternalJobController.java](/C:/2025proj/mini-shop/src/main/java/com/minishop/project/minishop/common/controller/InternalJobController.java)
- [OrderExpirationJobService.java](/C:/2025proj/mini-shop/src/main/java/com/minishop/project/minishop/order/service/OrderExpirationJobService.java)
- [RetryTaskJobService.java](/C:/2025proj/mini-shop/src/main/java/com/minishop/project/minishop/outbox/service/RetryTaskJobService.java)
