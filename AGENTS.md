# Repository Guidelines

## Project Structure & Module Organization
Source code is under `src/main/java/com/minishop/project/minishop`, organized by domain (`auth`, `user`, `product`, `inventory`, `order`, `payment`, `refund`, `outbox`, `common`) and then by layer (`controller`, `service`, `domain`, `repository`, `dto`, `event`).
Tests live in `src/test/java/com/minishop/project/minishop` and generally mirror the same domain structure.
Configuration is in `src/main/resources/application.properties`.
Design and rule documents are in `docs/` (notably `ARCHITECTURE.md`, `DOMAIN_RULES.md`, `PACKAGE_RULES.md`, `TEST_RULE.md`).

## Build, Test, and Development Commands
Use the Gradle wrapper, not a global Gradle install.

- `.\gradlew.bat clean`: Remove build outputs.
- `.\gradlew.bat build`: Compile, run tests, and package the app.
- `.\gradlew.bat bootRun`: Start the Spring Boot app locally.
- `.\gradlew.bat test`: Run all tests (JUnit Platform).
- `.\gradlew.bat test --tests "com.minishop.project.minishop.payment.service.PaymentServiceTest"`: Run one test class.
- `.\gradlew.bat test --tests "PaymentServiceTest.confirmPayment_..."`: Run one test method.

## Coding Style & Naming Conventions
Use Java 21 with 4-space indentation and standard Spring conventions.
Keep package names lowercase and class names `PascalCase`; methods/fields should be `camelCase`.
Follow feature-first packaging and layer boundaries from `docs/PACKAGE_RULES.md`.
Controllers should depend on services/DTOs, services on domain/repository/event, and domain code should stay framework-independent.
Test method names commonly follow `action_condition_expected` (underscores are acceptable).

## Testing Guidelines
Primary tools: JUnit 5, Spring Boot Test, AssertJ, and Awaitility.
Prefer fast domain unit tests for invariants, then service/integration tests for transactions and event flow.
Critical flows to cover: concurrency (inventory), idempotency (payment), and failure compensation/refund scenarios.
No explicit coverage threshold is enforced; rely on meaningful scenario coverage.

## Commit & Pull Request Guidelines
Commit messages in history follow a conventional prefix style such as `feat:`, `test:`, `refact:` with a concise subject.
Keep commits scoped to one change and include behavior impact in the message body when needed.
PRs should include:
- Problem and solution summary
- Affected domains/modules
- Test evidence (commands run and key results)
- API examples or screenshots for endpoint behavior changes
When architecture/package rules change, update related docs in `docs/` in the same PR.

## Security & Configuration Tips
Never commit secrets (JWT keys, payment credentials). Keep sensitive values in environment-specific configuration.
Document any new external integration settings and local run prerequisites in `docs/`.
