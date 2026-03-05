# GCP Cloud Run 배포 가이드

> mini-shop 개발 서버를 GCP Cloud Run(무료) + Supabase PostgreSQL + Upstash Redis로 배포하는 가이드.
> 민감 정보는 **GCP Secret Manager**로 관리한다.

## 아키텍처

```
┌─ GCP Cloud Run (Free) ──────────────────┐
│  Spring Boot Docker                      │
│  1Gi 메모리, min-instances=0             │
│                                          │
│  환경변수 ← GCP Secret Manager (Free)    │
│    DB_URL, DB_USER, DB_PASSWORD          │
│    REDIS_URL, JWT_SECRET                 │
│    TOSS_SECRET_KEY, TOSS_CLIENT_KEY      │
└──────┬──────────┬────────────────────────┘
       │          │
       ▼          ▼
 ┌──────────┐ ┌───────────┐
 │ Supabase │ │  Upstash  │
 │ Free PG  │ │ Free Redis│
 │ 500MB    │ │ 500K/월   │
 └──────────┘ └───────────┘
```

---

## 민감 정보 관리 전략

| 구분 | 저장 위치 | 예시 |
|------|----------|------|
| **시크릿** | GCP Secret Manager | DB 비밀번호, Redis URL, JWT 시크릿 |
| **비민감 설정** | Cloud Run 환경변수 (평문) | Spring 프로필, CORS 도메인 |
| **로컬 개발** | `.env.prod` (gitignored) | 개인 테스트용 값 보관 |

> `.env.example`에 템플릿이 있습니다. `.env.prod`로 복사하여 값을 채우세요.
> **`.env.prod`는 절대 커밋하지 마세요** (.gitignore에 등록됨).

---

## Step 1. GCP 프로젝트 준비

```bash
# gcloud CLI 설치: https://cloud.google.com/sdk/docs/install
gcloud auth login
gcloud projects create minishop-dev --name="MiniShop Dev"
gcloud config set project minishop-dev

# 필수 API 활성화
gcloud services enable run.googleapis.com
gcloud services enable artifactregistry.googleapis.com
gcloud services enable cloudbuild.googleapis.com
gcloud services enable secretmanager.googleapis.com
```

> 결제 계정 연결 필요 (무료 티어 내에서 과금 없음).

---

## Step 2. 외부 서비스 생성

### Supabase PostgreSQL

1. https://supabase.com 접속 → 회원가입
2. **New Project** → 리전: Northeast Asia (Tokyo) 또는 Southeast Asia (Singapore)
3. 프로젝트 생성 후 **Settings → Database** 에서 접속 정보 확인

> JDBC URL 변환이 필요합니다. Supabase가 제공하는 URI:
> ```
> postgresql://postgres.xxxx:PASSWORD@aws-0-ap-northeast-1.pooler.supabase.com:6543/postgres
> ```
> 를 아래처럼 분리:
> ```
> DB_URL  = jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:6543/postgres
> DB_USER = postgres.xxxx
> DB_PASSWORD = PASSWORD
> ```

4. **pg_trgm 확장 활성화** (상품 검색 인덱스용):
   - Supabase 대시보드 → **Database → Extensions** → `pg_trgm` 검색 → **Enable**

### Upstash Redis

1. https://upstash.com 접속 → 회원가입
2. **Create Database** → 리전: AP-Northeast-1 (Tokyo), **TLS 활성화** (기본값)
3. 생성 후 **Details** 에서 URL 복사:
   ```
   REDIS_URL = rediss://default:PASSWORD@xxxx.upstash.io:6379
   ```
   > **`rediss://`** (s 두 개)여야 TLS 연결됨.

---

## Step 3. Secret Manager에 시크릿 등록

`.env.prod`에 값을 채운 뒤 아래 스크립트로 일괄 등록합니다.

### 방법 A: 스크립트로 일괄 등록

```bash
# .env.example → .env.prod 복사 후 실제 값 채우기
cp .env.example .env.prod

# 시크릿 대상 변수 목록
SECRETS=(DB_URL DB_USER DB_PASSWORD REDIS_URL JWT_SECRET TOSS_SECRET_KEY TOSS_CLIENT_KEY)

for key in "${SECRETS[@]}"; do
  value=$(grep "^${key}=" .env.prod | cut -d'=' -f2-)
  if [ -n "$value" ]; then
    printf '%s' "$value" | gcloud secrets create "$key" \
      --data-file=- \
      --replication-policy=automatic 2>/dev/null \
    || printf '%s' "$value" | gcloud secrets versions add "$key" --data-file=-
    echo "✓ $key"
  fi
done
```

### 방법 B: 하나씩 수동 등록

```bash
# JWT 시크릿 생성 예시
openssl rand -base64 32 | gcloud secrets create JWT_SECRET \
  --data-file=- \
  --replication-policy=automatic

# DB 비밀번호 등록 예시
printf 'YOUR_SUPABASE_PASSWORD' | gcloud secrets create DB_PASSWORD \
  --data-file=- \
  --replication-policy=automatic
```

### Cloud Run 서비스 계정에 시크릿 접근 권한 부여

```bash
# 프로젝트 번호 확인
PROJECT_NUMBER=$(gcloud projects describe $(gcloud config get-value project) --format="value(projectNumber)")

# Cloud Run 기본 서비스 계정에 시크릿 접근 권한 부여
gcloud projects add-iam-policy-binding $(gcloud config get-value project) \
  --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

---

## Step 4. 배포

### 첫 배포

```bash
gcloud run deploy minishop \
  --source . \
  --region asia-northeast1 \
  --platform managed \
  --allow-unauthenticated \
  --memory 1Gi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 2 \
  --set-env-vars "SPRING_PROFILES_ACTIVE=prod" \
  --set-secrets "DB_URL=DB_URL:latest" \
  --set-secrets "DB_USER=DB_USER:latest" \
  --set-secrets "DB_PASSWORD=DB_PASSWORD:latest" \
  --set-secrets "REDIS_URL=REDIS_URL:latest" \
  --set-secrets "JWT_SECRET=JWT_SECRET:latest" \
  --set-secrets "TOSS_SECRET_KEY=TOSS_SECRET_KEY:latest" 
```

> `--set-secrets "ENV_VAR=SECRET_NAME:VERSION"` 형식.
> Cloud Run이 시작 시 Secret Manager에서 값을 가져와 환경변수로 주입합니다.
> 첫 빌드는 3~5분 소요.

```bash
--set-env-vars "CORS_ALLOWED_ORIGINS=https://your-frontend-domain.com" \
```

### 배포 확인

```bash
# 서비스 URL 확인
gcloud run services describe minishop \
  --region asia-northeast1 \
  --format="value(status.url)"

# 헬스체크
curl https://YOUR_SERVICE_URL/actuator/health
```

---

## 이후 배포 (코드 변경 시)

```bash
gcloud run deploy minishop \
  --source . \
  --region asia-northeast1
```

> 시크릿/환경변수는 이전 배포 설정이 유지됩니다.

### 시크릿 값 변경 시

```bash
# 새 버전 추가 (예: DB 비밀번호 변경)
printf 'NEW_PASSWORD' | gcloud secrets versions add DB_PASSWORD --data-file=-

# Cloud Run 재배포 (latest 버전이므로 새 인스턴스에 자동 반영)
gcloud run deploy minishop --source . --region asia-northeast1
```

---

## 환경변수 / 시크릿 목록

| 변수 | 유형 | 필수 | 설명 |
|------|------|------|------|
| `SPRING_PROFILES_ACTIVE` | 환경변수 | O | `prod` |
| `CORS_ALLOWED_ORIGINS` | 환경변수 | - | 프론트 도메인 (쉼표 구분) |
| `DB_URL` | **시크릿** | O | Supabase JDBC URL |
| `DB_USER` | **시크릿** | O | Supabase DB 사용자 |
| `DB_PASSWORD` | **시크릿** | O | Supabase DB 비밀번호 |
| `REDIS_URL` | **시크릿** | O | Upstash Redis TLS URL |
| `JWT_SECRET` | **시크릿** | O | JWT 서명 키 (256bit+) |
| `TOSS_SECRET_KEY` | **시크릿** | O | Toss Payments 시크릿 키 |
| `TOSS_CLIENT_KEY` | **시크릿** | O | Toss Payments 클라이언트 키 |
| `PORT` | 자동 | - | Cloud Run이 자동 주입 |

---

## 커스텀 도메인 연결 (선택)

```bash
gcloud run domain-mappings create \
  --service minishop \
  --domain your-domain.com \
  --region asia-northeast1
```

Cloud Run이 제공하는 DNS 레코드를 도메인 관리자에 등록하세요. SSL 인증서는 자동 발급됩니다.

---

## 비용

모든 서비스가 무료 티어 내에서 운영됩니다:

| 서비스 | 무료 한도 | 비고 |
|--------|----------|------|
| Cloud Run | 180K vCPU-초 + 360K GiB-초/월 | 1Gi 기준 ~100시간 |
| Cloud Build | 120분/일 빌드 시간 | 배포당 ~3분 |
| Secret Manager | 시크릿 6개 + 10K 접근/월 | 현재 7개 사용 (무료 내) |
| Supabase PG | 500MB 저장소 | 무기한 |
| Upstash Redis | 500K commands/월 | 무기한 |

> Secret Manager 무료 한도는 6개 **활성 시크릿 버전**입니다.
> 7개를 사용하므로 월 ~$0.06 (시크릿 1개 추가분) 발생 가능.
> 시크릿을 줄이려면 `DB_URL`에 user/password를 포함시켜 `DB_USER`, `DB_PASSWORD`를 제거하면 6개로 맞출 수 있습니다.

---

## 트러블슈팅

### Cold Start 느림 (~15-30초)
- Cloud Run이 0에서 깨어나는 시간. Java + Spring Boot 특성상 불가피.
- 프론트에서 로딩 스피너 표시 권장.

### OOM Kill
- Cloud Run 로그에서 `Memory limit exceeded` 확인 시 → 메모리를 `2Gi`로 올리기 (무료 한도 내 시간 감소).

### Supabase 연결 실패
- Connection pooling URL (port 6543) 사용 확인.
- Direct connection (port 5432)은 Cloud Run에서 불안정할 수 있음.

### Redis 연결 실패
- `rediss://` (TLS) 확인. `redis://`로 하면 Upstash 거부.

### Secret Manager 권한 오류
- `PERMISSION_DENIED` → Step 3의 IAM 권한 부여 명령어 재실행.
- 서비스 계정 이메일 확인: `gcloud run services describe minishop --region asia-northeast1 --format="value(spec.template.spec.serviceAccountName)"`

### pg_trgm 인덱스 생성 실패
- Supabase 대시보드 → Extensions에서 pg_trgm 수동 활성화.
- 실패해도 앱 동작에 영향 없음 (검색 속도만 저하).
