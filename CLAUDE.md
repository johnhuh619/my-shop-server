# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

mini-shop is a Spring Boot 4.0.0 application built with Java 21 using Gradle. The project includes Spring Security and Spring Web MVC for building a secure web application.

## Build and Run Commands

### Build the project
```bash
./gradlew build
```

### Run the application
```bash
./gradlew bootRun
```

### Run tests
```bash
./gradlew test
```

### Run a single test class
```bash
./gradlew test --tests "com.minishop.project.minishop.MiniShopApplicationTests"
```

### Run a specific test method
```bash
./gradlew test --tests "ClassName.methodName"
```

### Clean build artifacts
```bash
./gradlew clean
```

### View all available tasks
```bash
./gradlew tasks
```

## Project Structure

This is a standard Spring Boot application following Maven/Gradle conventions:

- **src/main/java/com/minishop/project/minishop/** - Main application code
  - `MiniShopApplication.java` - Spring Boot entry point with `@SpringBootApplication`
- **src/main/resources/** - Configuration files
  - `application.properties` - Spring Boot configuration (currently minimal)
- **src/test/java/** - Test code using JUnit Platform
- **build.gradle** - Gradle build configuration

## Technology Stack

- **Java 21** - Required JDK version (configured via toolchain)
- **Spring Boot 4.0.0** - Framework for the application
- **Spring Security** - Security and authentication
- **Spring Web MVC** - Web layer and REST endpoints
- **Lombok** - Reduces boilerplate code (compile-only dependency)
- **JUnit Platform** - Testing framework

## Development Notes

### Package Naming
The base package is `com.minishop.project.minishop`. All new Java classes should be created under this package or its sub-packages to ensure Spring Boot component scanning works correctly.

### Lombok
Project uses Lombok for reducing boilerplate. Annotation processor is configured in build.gradle. Common annotations like `@Data`, `@Builder`, `@Slf4j` are available.

### Testing
Tests use Spring Boot test starters including:
- `spring-boot-starter-webmvc-test` for web layer testing
- `spring-boot-starter-security-test` for security testing
- `awaitility` for asynchronous event testing

JUnit Platform is configured as the test runtime.

### Gradle Wrapper
Use `./gradlew` (or `gradlew.bat` on Windows) instead of a globally installed Gradle to ensure consistent build environment.

## Architecture & Design Rules

**IMPORTANT: Claude MUST follow these documents strictly before writing any code.**

### Required Reading (in order)
1. `docs/ARCHITECTURE.md` - System architecture and design decisions (WHY)
2. `docs/DOMAIN_RULES.md` - Domain invariants and business rules (WHAT MUST NOT BREAK)
3. `docs/PACKAGE_RULES.md` - Package structure and dependency rules (HOW CODE IS ORGANIZED)

### Mandatory Rules

#### Package Rules
- All classes MUST be under `com.minishop.project.minishop`
- Follow feature-based package structure: `{domain}/{layer}`
- Allowed layers: `controller`, `service`, `domain`, `repository`, `dto`, `event`
- **If a new class does not clearly belong to a package, STOP and ask before creating it.**

#### Dependency Rules
- Controller → Service, DTO only (NO direct Repository/Domain access)
- Service → Domain, Repository, Event only (NO Controller access)
- Domain → Java standard library only (NO Spring dependencies)
- Repository → Domain only

#### Domain Rules
- Order, OrderItem, Payment, Refund: **snapshot data is immutable after creation**
- OrderItem must copy Product data (productName, unitPrice, quantity)
- Payment requires `(user_id, idempotency_key)` UNIQUE constraint
- Inventory changes only through reserve/release operations

#### Cross-Domain Rules
- Order → Product: **direct reference forbidden**
- OrderItem → Product: **snapshot only**
- Payment → Order: **no direct state modification**

#### Forbidden Actions(ABSOLUTE)
Claudec MUST NOT:
- Refactor unrelated Code
- Change Architecture or domain Boundary
- Introduce new dependency (except approved test dependencies: Awaitility)
- Modify Spring Security configuration
- Add logic that bypass validation or idempotency
- Move or rename packages
- Assume events are processed only one

#### Transaction & Event Safety
- DB is the Source of Truth
- Outbox records must be created inside DB transactions
- Redis/ Stream is delivery only
- Workers MUST be idempotent and duplication-safe
- Long or Slow operations MUST be async

if transaction boundaries are unclear: STOP and ASK before coding

### Before Creating New Code
1. Check if the domain exists in `docs/ARCHITECTURE.md`
2. Verify the rule compliance with `docs/DOMAIN_RULES.md`
3. Confirm the package location with `docs/PACKAGE_RULES.md`
4. If unclear, **ask before implementing**