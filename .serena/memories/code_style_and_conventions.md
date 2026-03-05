# Code Style & Conventions

## Package Structure
- Base package: `com.minishop.project.minishop`
- Feature-based: `{domain}/{layer}` (controller, service, domain, repository, dto, event)
- Each domain has `package-info.java` in every layer

## Entity Style
- `@Entity` + `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
- `@Builder` on constructor (not class-level)
- Static factory method: `Entity.create(...)` for creation logic
- State transitions as domain methods: `order.cancel()`, `order.markAsPaid()`
- Throw `BusinessException(ErrorCode.XXX, "message")` for invariant violations
- Timestamps: `Instant` type (`createdAt`, `updatedAt`)

## Controller Style
- `@RestController` + `@RequestMapping("/api/{domain}")`
- `@RequiredArgsConstructor` for DI
- Return `ApiResponse<T>` wrapper: `ApiResponse.success(data)`
- Get userId via `AuthenticationContext.getCurrentUserId()`
- DTO conversion in controller: `Response.from(entity)`

## Service Style
- `@Service` + `@RequiredArgsConstructor`
- `@Transactional` for write operations
- Uses Spring `ApplicationEventPublisher` for events

## DTO Style
- Request: simple POJOs with Lombok
- Response: static `from(Entity)` factory method

## Test Style
- Korean test method naming: `메서드_시나리오_예상결과()`
- Domain tests: pure unit tests (no Spring context)
- Service tests: `@SpringBootTest` + `@Transactional`
- Concurrency tests: no `@Transactional`, manual cleanup
- Async tests: Awaitility

## Dependency Rules (Absolute)
- Controller → Service, DTO only
- Service → Domain, Repository, Event only
- Domain → Java standard library only (no Spring deps)
- Repository → Domain only
