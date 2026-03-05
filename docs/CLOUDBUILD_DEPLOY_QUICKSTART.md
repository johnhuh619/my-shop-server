# Cloud Build 배포 퀵스타트

`mini-shop` 백엔드를 `cloudbuild.yaml`로 배포할 때 쓰는 최소 절차.

기준 파일: `cloudbuild.yaml`
- region: `asia-northeast1`
- service: `minishop`
- service account: `minishop-runner@<PROJECT_ID>.iam.gserviceaccount.com`

---

## 1) 1회 초기 세팅

```powershell
$PROJECT_ID = "minishop-dev"
$REGION = "asia-northeast1"

gcloud auth login
gcloud config set project $PROJECT_ID

gcloud services enable run.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com secretmanager.googleapis.com

gcloud artifacts repositories create cloud-run-source-deploy `
  --repository-format=docker `
  --location=$REGION `
  --description="Cloud Run images"
```

---

## 2) 서비스 계정 + 권한 (1회)

```powershell
gcloud iam service-accounts create minishop-runner --display-name="MiniShop Run SA"

$PROJECT_NUMBER = gcloud projects describe $PROJECT_ID --format="value(projectNumber)"

gcloud projects add-iam-policy-binding $PROJECT_ID `
  --member="serviceAccount:minishop-runner@$PROJECT_ID.iam.gserviceaccount.com" `
  --role="roles/secretmanager.secretAccessor"

gcloud projects add-iam-policy-binding $PROJECT_ID `
  --member="serviceAccount:$PROJECT_NUMBER@cloudbuild.gserviceaccount.com" `
  --role="roles/run.admin"

gcloud projects add-iam-policy-binding $PROJECT_ID `
  --member="serviceAccount:$PROJECT_NUMBER@cloudbuild.gserviceaccount.com" `
  --role="roles/artifactregistry.writer"

gcloud iam service-accounts add-iam-policy-binding `
  "minishop-runner@$PROJECT_ID.iam.gserviceaccount.com" `
  --member="serviceAccount:$PROJECT_NUMBER@cloudbuild.gserviceaccount.com" `
  --role="roles/iam.serviceAccountUser"
```

---

## 3) Secret Manager 준비

`cloudbuild.yaml` 기준 주입 시크릿:
- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `REDIS_URL`
- `JWT_SECRET`
- `TOSS_SECRET_KEY`

등록 예시:

```powershell
echo -n "jdbc:postgresql://..." | gcloud secrets create DB_URL --data-file=- --replication-policy=automatic
```

이미 있으면:

```powershell
echo -n "new-value" | gcloud secrets versions add DB_URL --data-file=-
```

---

## 4) 배포

```powershell
gcloud builds submit --config cloudbuild.yaml
```

태그 지정 배포:

```powershell
gcloud builds submit --config cloudbuild.yaml --substitutions=_TAG=20260303-1
```

---

## 5) 배포 확인

```powershell
$URL = gcloud run services describe minishop --region asia-northeast1 --format="value(status.url)"
curl "$URL/actuator/health"
```

