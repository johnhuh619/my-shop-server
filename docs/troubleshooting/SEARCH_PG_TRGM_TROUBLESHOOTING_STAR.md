# Product Search pg_trgm Troubleshooting (STAR)

작성일: 2026-02-14  
범위:
- `src/main/java/com/minishop/project/minishop/product/repository/ProductRepository.java`
- `src/main/java/com/minishop/project/minishop/common/config/PostgresTrigramIndexInitializer.java`
- `search_sol.md`

## 목적
이 문서는 상품 검색 성능 개선을 위해 `pg_trgm`을 도입한 트러블슈팅 과정을 STAR 방식으로 정리한 기록이다.  
특히 "부분 문자열 검색(`%keyword%`)을 유지하면서" 운영 환경(PostgreSQL)에서 성능을 개선하고, 개발/테스트 환경(H2) 호환성을 깨지 않게 만든 결정 과정을 남긴다.

---

## 배경 요약

### 1) 기존 검색 구조
- API: `GET /api/products?keyword=...`
- 서비스: `ProductService#getActiveProductsWithStock(...)`
- 저장소: `ProductRepository#findByStatusAndNameContainingIgnoreCase(...)`
- 요구사항: 검색 UX를 바꾸지 않고 부분 문자열 검색 유지

### 2) 환경 제약
- 기본 로컬/테스트: H2
- Docker/운영: PostgreSQL (Supabase 포함)
- 즉, PostgreSQL 전용 최적화는 필요하지만 H2에서도 애플리케이션이 정상 기동되어야 함

### 3) 후보 검토 결과 (`search_sol.md`)
- Prefix-only: 구현 단순하나 검색 정확도 낮음
- PostgreSQL FTS: 단어 기반 검색에 유리하나 `%keyword%`와 목적이 다름
- Elasticsearch: 기능은 최고지만 인프라 비용/복잡도 큼
- Redis 검색 캐시: 동기화/메모리 관리 부담
- **pg_trgm: 현재 요구(부분 문자열 + 최소 코드 변경)에 가장 적합**

---

## STAR

## S (Situation)
상품 검색이 `%keyword%` 형태의 부분 문자열 검색이어서, 데이터 증가 시 Full Scan 리스크가 있었다.  
동시에 프로젝트는 H2와 PostgreSQL을 혼용하므로, 단순히 PostgreSQL 전용 SQL을 고정하면 비운영 환경이 깨질 가능성이 있었다.

관측/문제 포인트:
1. 검색 기능은 이미 비즈니스 경로에 결합되어 있어 API 스펙 변경이 어려움
2. 운영 DB는 PostgreSQL이므로 인덱스 최적화 여지가 큼
3. 하지만 개발/테스트 H2를 고려해 안전한 분기 처리가 필요

## T (Task)
다음 조건을 모두 만족하도록 검색 최적화를 구현한다.
1. 기존 부분 문자열 검색 UX 유지
2. PostgreSQL에서 `pg_trgm` 확장 + 인덱스로 검색 성능 개선
3. H2 환경에서 오류 없이 동작
4. 코드 변경 범위 최소화, 회귀 위험 최소화

## A (Action)

### 1) 검색 경로와 실제 쿼리 지점 식별
- 검색 호출 체인을 확인해 변경 지점을 Repository로 좁힘
- 대상 메서드:
  - `ProductService#getActiveProductsWithStock(...)`
  - `ProductRepository#findByStatusAndNameContainingIgnoreCase(...)`

### 2) Repository 쿼리 명시화
- 변경 파일: `src/main/java/com/minishop/project/minishop/product/repository/ProductRepository.java`
- 기존 파생 메서드를 명시 JPQL로 고정:
  - `lower(p.name) LIKE concat('%', lower(:name), '%')`
- 이유:
  - SQL 형태를 명시적으로 통제
  - `lower(name)` 기반 인덱스와 쿼리의 정합성을 확보

핵심 코드:
```java
@Query("""
        SELECT p
        FROM Product p
        WHERE p.status = :status
          AND lower(p.name) LIKE concat('%', lower(:name), '%')
        """)
Page<Product> findByStatusAndNameContainingIgnoreCase(
        @Param("status") ProductStatus status,
        @Param("name") String name,
        Pageable pageable
);
```

### 3) PostgreSQL 전용 초기화 컴포넌트 추가
- 신규 파일: `src/main/java/com/minishop/project/minishop/common/config/PostgresTrigramIndexInitializer.java`
- 구현 방식:
  - 앱 시작 시 DB 메타데이터로 PostgreSQL 여부 확인
  - PostgreSQL인 경우에만 아래 SQL 실행
    1. `CREATE EXTENSION IF NOT EXISTS pg_trgm`
    2. `CREATE INDEX IF NOT EXISTS idx_products_name_lower_trgm ON products USING gin (lower(name) gin_trgm_ops)`
  - 실패 시 앱 중단 대신 `warn` 로그로 처리

핵심 설계 이유:
1. H2 환경에서 PostgreSQL 전용 SQL 실행 방지
2. 배포 시 수동 DDL 누락 리스크 감소
3. 인덱스 생성/확장 생성의 idempotent 보장(`IF NOT EXISTS`)

### 4) 회귀 검증
- 실행: `.\gradlew.bat test --tests "com.minishop.project.minishop.product.service.ProductServiceTest"`
- 결과: 통과
- 확인 포인트:
  - 기존 검색/페이징/재고 매핑 동작 유지
  - 변경이 서비스 레벨 계약을 깨지 않음

## R (Result)
1. 검색 UX(부분 문자열 검색)를 유지한 채 PostgreSQL에서 인덱스 최적화 경로 확보
2. 앱 시작 시 자동으로 `pg_trgm` 확장/인덱스를 준비해 운영 실수 가능성 감소
3. H2 환경에서는 초기화 로직이 안전하게 스킵되어 개발/테스트 호환성 유지
4. 서비스 테스트 기준 기능 회귀 없음

---

## 설계 판단 근거 (왜 이 방식인가)

### 1) 왜 Elasticsearch가 아닌가
- 현재 요구는 "부분 문자열 검색 최적화" 중심
- Elasticsearch는 과한 인프라/운영 복잡도를 유발
- 현 단계에서는 `pg_trgm`의 비용 대비 효과가 더 큼

### 2) 왜 FTS가 아닌가
- FTS는 토큰(단어) 기반 검색에 강점이 있음
- 이번 요구는 `%keyword%` 기반 부분 문자열 검색으로 성격이 다름

### 3) 왜 초기화 코드를 애플리케이션에 넣었는가
- 수동 SQL 실행 누락 방지
- 환경별 자동 분기(PostgreSQL/H2)로 운영 안정성 향상
- 조건부 + idempotent SQL로 반복 배포에도 안전

---

## 운영 체크리스트
1. 운영 PostgreSQL 계정에 `CREATE EXTENSION` 권한이 있는지 확인
2. 초기 기동 로그에서 `"Initialized pg_trgm extension and product name trigram index"` 메시지 확인
3. 필요 시 `EXPLAIN ANALYZE`로 `idx_products_name_lower_trgm` 사용 여부 확인
4. 대용량 데이터에서 `keyword` 길이(특히 1~2글자)별 응답시간 측정 및 임계값 정의

---

## 후속 개선 제안
1. 검색 성능 테스트 스크립트(k6/JMeter) 추가로 p95/p99 관리
2. `keyword` 길이에 따른 검색 정책(최소 글자 수, prefix fallback) 명문화
3. 운영 관측 지표에 검색 응답시간/슬로우쿼리 알림 연결

---

## 운영 관점 결론
1. 운영 환경에서는 애플리케이션 런타임 DDL을 지양한다.
2. `pg_trgm` 확장/인덱스는 Flyway/Liquibase 또는 DBA 수동 SQL로 선반영한다.
3. 앱 계정은 DDL 권한 없이 읽기/쓰기 중심 권한만 부여한다.
4. 현재 `PostgresTrigramIndexInitializer`는 개발 편의용 임시 코드이며, 운영 마이그레이션 전환 후 제거한다.

---

## 현재 상태 업데이트 (2026-02-17)

### 반영 완료
1. 검색 Repository 쿼리를 `lower(name) LIKE '%keyword%'` 형태로 명시화했다.
2. `PostgresTrigramIndexInitializer`에 개발 편의용 임시 코드 주석을 추가했다.
3. `PostgresTrigramIndexInitializer`는 `default/dev/docker` 프로필에서만 동작하도록 제한했다.
4. Docker 기준 DB 드라이버를 PostgreSQL로 정리했다.

### 아직 남은 작업 (운영 전환)
1. Flyway 또는 Liquibase 도입 후 `pg_trgm` 확장/인덱스를 마이그레이션 SQL로 이관
2. 운영 프로필의 `spring.jpa.hibernate.ddl-auto`를 `validate` 또는 `none`으로 전환
3. `PostgresTrigramIndexInitializer` 제거
4. MySQL 기준으로 남아있는 문서(`docs/LOAD_TEST.md`)를 PostgreSQL 기준으로 정합성 수정
