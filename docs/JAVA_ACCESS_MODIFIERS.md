# Java 접근 제어자 (Access Modifiers)

> 이 문서는 Java 접근 제어자의 동작 원리와 Spring Bean 관리에서의 의미를 정리한다.
> mini-shop 프로젝트에서 `PaymentConfirmHandler`를 package-private으로 설계한 근거이기도 하다.

---

## 1. 4가지 접근 수준

```
넓음 ◄──────────────────────────────────────► 좁음
public       protected       (생략)        private
                           package-private
```

| 접근 제어자 | 같은 클래스 | 같은 패키지 | 자식 클래스 | 외부 전체 |
|------------|:---------:|:---------:|:---------:|:--------:|
| `public` | O | O | O | O |
| `protected` | O | O | O | X |
| (생략) package-private | O | O | X | X |
| `private` | O | X | X | X |

---

## 2. 클래스 레벨 (top-level class)

top-level 클래스에는 **`public`과 생략** 두 가지만 가능하다.

```java
// 파일: Foo.java
public class Foo { }   // 어디서든 import해서 사용 가능
class Bar { }          // 같은 패키지에서만 사용 가능
```

`private class`나 `protected class`는 top-level에서 **컴파일 에러**이다.
- `protected`: top-level 클래스는 "부모 클래스"가 없으므로 "자식에게 공개"가 의미 없음
- `private`: 아무도 쓸 수 없어서 존재 의미가 없음

---

## 3. 멤버 레벨 (필드, 메서드, 내부 클래스)

클래스 안의 멤버에는 4가지 모두 사용 가능하다.

```java
public class User {
    public String name;           // 어디서든 접근
    protected String email;       // 같은 패키지 + 자식 클래스
    String nickname;              // 같은 패키지만 (package-private)
    private Long id;              // 이 클래스 안에서만

    private static class Address { }  // 내부 클래스는 private 가능
}
```

---

## 4. "같은 패키지"의 정의

```
com.minishop.payment.service
├── PaymentService.java          ← 같은 패키지
├── PaymentConfirmHandler.java   ← 같은 패키지 → 서로 package-private 접근 가능

com.minishop.order.service
└── OrderService.java            ← 다른 패키지 → package-private 접근 불가
```

**주의: 하위 패키지는 "같은 패키지"가 아니다.**

```
com.minishop.payment           ← 패키지 A
com.minishop.payment.service   ← 패키지 B (A와 별개)
```

Java는 패키지 계층을 인식하지 않는다. `payment`와 `payment.service`는 완전히 다른 패키지이다.

---

## 5. protected가 헷갈리는 이유

`protected`는 **같은 패키지 + 자식 클래스**이다. 패키지가 달라도 상속하면 접근 가능하다.

```java
// com.minishop.payment.service
public class PaymentService {
    protected void validate() { }
}

// com.minishop.order.service (다른 패키지)
public class SpecialPaymentService extends PaymentService {
    public void doSomething() {
        validate();  // O - 자식이니까 접근 가능
    }
}

public class OrderService {
    public void doSomething(PaymentService ps) {
        ps.validate();  // X - 자식이 아니니까 접근 불가
    }
}
```

---

## 6. 컴파일 타임 vs 런타임

접근 제어자는 **컴파일러가 강제하는 규칙**이다. 런타임에 리플렉션을 사용하면 우회할 수 있다.

```java
// 컴파일 타임 - 접근 제어자 적용
class Foo { }
// 다른 패키지에서:
Foo foo = new Foo();  // X - 컴파일 에러

// 런타임 (리플렉션) - 우회 가능
Class<?> clazz = Class.forName("com.example.Foo");
Constructor<?> ctor = clazz.getDeclaredConstructor();
ctor.setAccessible(true);   // 접근 제어 해제
Object foo = ctor.newInstance();  // O - 성공
```

---

## 7. Spring Bean과 접근 제어자

Spring은 리플렉션으로 Bean을 생성하므로 package-private 클래스도 문제없이 관리할 수 있다.

```java
// package-private 클래스도 @Service만 있으면 Bean 등록됨
@Service
class PaymentConfirmHandler { ... }
```

동작 원리:

```
@SpringBootApplication (컴포넌트 스캔)
  └─ PaymentConfirmHandler 발견 (@Service)
  └─ 리플렉션으로 인스턴스 생성 (접근 제어 무관)
  └─ AOP 프록시 생성 (@Transactional 적용)
  └─ PaymentService 생성자 주입 시 confirmHandler로 주입
```

결과:
- **Spring에게는** 정상적인 Bean (리플렉션으로 생성)
- **같은 패키지 `PaymentService`에게는** 컴파일 타임에 직접 참조 가능
- **다른 패키지에게는** 컴파일 에러로 접근 차단 (캡슐화)

---

## 8. 프로젝트 적용 사례

`PaymentConfirmHandler`를 package-private으로 설계한 이유:

| 관점 | 설명 |
|------|------|
| 캡슐화 | confirm 트랜잭션 로직은 `PaymentService`의 내부 구현 세부사항이므로 외부에 노출할 필요 없음 |
| Spring 호환 | 리플렉션 기반이므로 `@Service`, `@Transactional` 정상 동작 |
| 실수 방지 | 다른 도메인(order, refund 등)에서 `PaymentConfirmHandler`를 직접 호출하는 것을 컴파일러가 차단 |
| 단일 진입점 | 결제 관련 요청은 반드시 `PaymentService`(public)를 통해서만 접근 가능 |
