# Package Rules

> **mini-shop 프로젝트의 패키지 구조 정의서**

이 문서는 mini-shop 프로젝트의 패키지 구조를 정의한다.
**모든 코드는 이 구조를 반드시 따른다.**

---

## 1. Base Package

모든 코드는 아래 base package 하위에 위치해야 한다.

```
com.minishop.project.minishop
```

---

## 2. Top-Level Package Structure

```
com.minishop.project.minishop
├── auth          # 인증 / 인가
├── user          # 사용자
├── product       # 상품
├── inventory     # 재고 (경쟁 자원)
├── order         # 주문
├── payment       # 결제
├── refund        # 환불
├── outbox        # 이벤트 브릿지
└── common        # 공통 (exception, response, util)
```

### 금지 사항

```
❌ domain, service, controller 같은 기술 중심 패키지
❌ feature와 무관한 util 난립
```

---

## 3. Domain-Internal Structure (Feature-based)

각 도메인은 아래 구조를 따른다.

```
{domain}/
├── controller    # HTTP 요청/응답
├── service       # 비즈니스 로직
├── domain        # 엔티티 / 값 객체
├── repository    # JPA 접근
├── dto           # 외부 통신용 객체
└── event         # 도메인 이벤트
```

### 역할 규칙

| 패키지 | 책임 |
|--------|------|
| controller | HTTP 요청/응답 처리 |
| service | 비즈니스 로직 구현 |
| domain | 엔티티 / 값 객체 정의 |
| repository | JPA 데이터 접근 |
| dto | 외부 통신용 객체 (Request/Response) |
| event | 도메인 이벤트 정의 및 발행 |

---

## 4. Dependency Rules (절대 규칙)

### 4.1 Controller

| 접근 가능 | 접근 금지 |
|-----------|----------|
| service, dto | domain, repository |

```
❌ Controller에서 Repository 직접 접근 금지
❌ Controller에서 Domain 직접 접근 금지
```

### 4.2 Service

| 접근 가능 | 접근 금지 |
|-----------|----------|
| domain, repository, event | controller |

```
❌ Service에서 Controller 접근 금지
```

### 4.3 Domain

| 접근 가능 | 접근 금지 |
|-----------|----------|
| Java 표준 라이브러리 | controller, service, dto |

```
❌ Domain에서 Controller 의존 금지
❌ Domain에서 Service 의존 금지
❌ Domain에서 DTO 의존 금지
```

### 4.4 Repository

| 접근 가능 | 접근 금지 |
|-----------|----------|
| domain | controller, service, dto |

---

## 5. Cross-Domain Rules

도메인 간 참조 규칙:

| 규칙 | 설명 |
|------|------|
| Order → Product | **직접 참조 금지** |
| OrderItem → Product | **스냅샷만 허용** |
| Payment → Order | **상태 직접 변경 금지** |
| Inventory 접근 | **Order 생성 과정에서만** |

---

## 6. Forbidden Patterns

다음 패턴은 사용을 금지한다:

```
❌ common.domain.*
❌ common.entity.*
❌ global.service.*
❌ 도메인 간 util 공유
```

---

## 7. 변경 규칙

- 패키지 구조 변경은 **아키텍처 변경**으로 간주
- 변경 시 필수 작업:
  - `ARCHITECTURE.md` 수정
  - `PACKAGE_RULES.md` 수정

---

## 부록 A: 의존성 다이어그램

```
┌─────────────────────────────────────────────────────────┐
│                      Controller                          │
│                  (HTTP 요청/응답)                         │
└─────────────────────┬───────────────────────────────────┘
                      │ 접근 가능
                      ▼
┌─────────────────────────────────────────────────────────┐
│                       Service                            │
│                  (비즈니스 로직)                          │
└───────┬─────────────────────────────────┬───────────────┘
        │ 접근 가능                        │ 접근 가능
        ▼                                 ▼
┌───────────────────┐           ┌─────────────────────────┐
│    Repository     │           │         Event           │
│   (JPA 접근)      │           │    (도메인 이벤트)       │
└─────────┬─────────┘           └─────────────────────────┘
          │ 접근 가능
          ▼
┌─────────────────────────────────────────────────────────┐
│                       Domain                             │
│                (엔티티 / 값 객체)                         │
│              Java 표준 라이브러리만 의존                   │
└─────────────────────────────────────────────────────────┘
```

---

## 부록 B: ArchUnit 테스트 예시

패키지 규칙을 코드로 강제하기 위한 ArchUnit 테스트 예시:

```java
@AnalyzeClasses(packages = "com.minishop.project.minishop")
public class ArchitectureTest {

    @Test
    void controller_should_not_access_repository() {
        noClasses()
            .that().resideInAPackage("..controller..")
            .should().accessClassesThat()
            .resideInAPackage("..repository..")
            .check(classes);
    }

    @Test
    void domain_should_not_depend_on_service_or_controller() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..service..", "..controller..")
            .check(classes);
    }
}
```

---

## 부록 C: AI 개발 프롬프트 템플릿

새로운 클래스 생성 시 사용할 프롬프트:

```
You must follow PACKAGE_RULES.md.

Create:
- package: com.minishop.project.minishop.{domain}.{layer}
- class: {ClassName}

Do NOT:
- create new top-level packages
- bypass layer rules
- move existing classes
```

---

## 부록 D: 패키지 생성 체크리스트

새로운 클래스 생성 전 확인:

- [ ] Base package(`com.minishop.project.minishop`) 하위인가?
- [ ] 올바른 도메인 패키지에 속하는가?
- [ ] 올바른 레이어(controller/service/domain/repository/dto/event)에 속하는가?
- [ ] 의존성 규칙을 위반하지 않는가?
- [ ] 금지된 패턴을 사용하지 않는가?
