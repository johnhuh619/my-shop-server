# 검색 솔루션 정리 (mini-shop)

## 1) 목표
상품명 검색에서 `LIKE '%keyword%'` 형태의 **부분 문자열 검색**을 빠르게 처리하고, 유지보수 비용을 최소화한다.

## 2) 후보별 비교

### A. Prefix-only (`LIKE 'keyword%'`)
- 쿼리 예시: `WHERE status = 'ACTIVE' AND name LIKE 'keyword%'`
- 장점: 구현이 가장 단순, B-tree 인덱스(`status, name`) 활용 가능
- 한계: 접두어 검색만 가능. 예: `사과 주스`는 찾지만 `유기농 사과`는 못 찾을 수 있음

### B. DB Full-Text Search (PostgreSQL FTS)
- 개념: `to_tsvector` + GIN 인덱스로 단어 단위 검색
- 장점: 단어 기반 검색 정확도 우수, DB 내 처리
- 한계: `%keyword%` 같은 부분 문자열 검색과는 성격이 다름(토큰 경계 기반)

### C. PostgreSQL `pg_trgm` (권장)
- 개념: 문자열을 3글자 단위(trigram)로 분해해 GIN 인덱스로 `LIKE/ILIKE '%keyword%'` 가속
- 장점: 기존 Repository/쿼리 변경 최소(DDL 중심), 부분 문자열 검색에 강함
- 한계: PostgreSQL 전용 기능

### D. Elasticsearch
- 장점: 가장 강력한 검색 기능(오타 보정, 분석기, 랭킹 튜닝)
- 한계: 인프라/운영 복잡도와 동기화 비용이 큼

### E. Redis 기반 검색 캐시
- 장점: 이미 Redis를 쓰고 있다면 빠르게 붙일 수 있음
- 한계: 인덱싱/동기화 로직을 직접 관리해야 하며 데이터 증가 시 메모리 부담

## 3) 요약 매트릭스
| 옵션 | 구현 비용 | 검색 품질 | 인프라 변경 | 코드 변경 | 비고 |
|---|---|---|---|---|---|
| Prefix-only | 낮음 | 낮음 | 없음 | 매우 적음 | 접두어만 검색 |
| PostgreSQL FTS | 중간 | 중간~높음 | PostgreSQL 기능 의존 | 중간 | 단어 기반 검색 |
| **PostgreSQL pg_trgm** | **낮음~중간** | **높음(부분 문자열)** | **PostgreSQL 확장 필요** | **낮음(DDL 위주)** | **현재 요구와 가장 잘 맞음** |
| Redis 캐시 | 중간 | 구현 방식에 따라 다름 | 없음(기존 Redis 활용) | 중간~높음 | 동기화 관리 필요 |
| Elasticsearch | 높음 | 최고 | 별도 클러스터 운영 | 높음 | 대규모/고급 검색에 적합 |

## 4) mini-shop 현재 기준 결론
레포 설정 기준:
- 기본(`application.properties`): H2
- Docker(`application-docker.properties`, `docker-compose.yml`): PostgreSQL
- Redis: 사용 중

따라서 현재 프로젝트에서는 **`pg_trgm` + 기존 `ILIKE '%keyword%'` 유지**가 비용 대비 효과가 가장 좋다.

## 5) 즉시 적용 SQL (PostgreSQL)
```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_products_name_trgm
ON products USING gin (name gin_trgm_ops);
```

기존 쿼리는 그대로 유지 가능:
```sql
WHERE status = 'ACTIVE' AND name ILIKE '%keyword%'
```

## 6) 버전/호환성 메모
- `pg_trgm`은 PostgreSQL 생태계에서 널리 지원되는 표준 확장이다.
- 운영 DB가 PostgreSQL(Supabase 포함)이라면 실무 적용 장벽이 낮다.
- H2 테스트 환경에서는 `pg_trgm` 동작 자체를 재현할 수 없으므로, 검색 성능 검증은 PostgreSQL 통합 테스트로 분리하는 것이 안전하다.
