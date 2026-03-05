# 인증 플로우 (Authentication Flow)

이 문서는 mini-shop 애플리케이션의 인증 및 권한 부여 플로우를 설명합니다.

## 개요

애플리케이션은 **JWT 기반 무상태(stateless) 인증**을 Spring Security와 통합하여 사용합니다. 인증은 전적으로 `auth` 도메인에서 처리하며, 사용자 관리 관심사와 명확하게 분리되어 있습니다.

## 아키텍처 원칙

### 책임 분리

- **TokenService**: 토큰 생성 및 검증 로직만 담당
  - `issueToken(userId)`: JWT 토큰 생성
  - `validateAccessToken(token)`: 토큰 검증 및 TokenPayload 반환

- **AuthService**: 인증 오케스트레이션
  - `login(email, password)`: 로그인 플로우 조율
  - User 도메인(사용자 조회)과 Token 도메인(토큰 발급) 간 조정

- **UserService**: 사용자 관리만 담당
  - 비밀번호 인코딩을 포함한 회원가입 (데이터 저장 관심사)
  - 사용자 CRUD 작업
  - 인증 로직 없음

### 인증 로직 유출 방지

- User 도메인은 인증을 처리하지 않음
- User 도메인은 인코딩된 비밀번호만 저장
- 비밀번호 검증은 Auth 도메인에서만 수행
- 토큰 검증은 Auth 도메인에서만 수행

## 인증 플로우

### 1. 사용자 회원가입

```
Client → POST /api/users/register
         ↓
UserController → UserService.registerUser()
                  ↓
                  1. 이메일 중복 확인
                  2. 비밀번호 인코딩 (BCrypt)
                  3. User 엔티티 생성
                  4. DB 저장
                  ↓
         ← UserResponse (토큰 없음)
```

**핵심 포인트:**
- 저장 전 BCrypt로 비밀번호 인코딩
- 회원가입 시 인증 토큰 발급하지 않음
- 액세스 토큰을 얻으려면 별도 로그인 필요

### 2. 로그인 (토큰 발급)

```
Client → POST /api/auth/login {email, password}
         ↓
AuthController → AuthService.login()
                  ↓
                  1. 이메일로 사용자 조회
                  2. 비밀번호 검증 (BCrypt)
                  3. 사용자 상태 확인 (ACTIVE)
                  4. JWT 토큰 발급
                  ↓
                 TokenService.issueToken(userId)
                  ↓
         ← LoginResponse {accessToken, userId, email, name}
```

**핵심 포인트:**
- AuthService는 User 도메인에서 읽기만 수행 (읽기 전용)
- 비밀번호 검증은 PasswordEncoder.matches() 사용
- ACTIVE 사용자만 로그인 가능
- 토큰은 userId만 포함 (역할/권한 없음)

### 3. 요청 인증 (토큰 검증)

```
Client → Authorization: Bearer {token} 헤더와 함께 요청
         ↓
JwtAuthenticationFilter.doFilterInternal()
         ↓
         1. Authorization 헤더에서 토큰 추출
         2. 토큰 검증
         ↓
        TokenService.validateAccessToken(token)
         ↓ (유효한 경우)
         3. AuthenticatedUser(userId) 생성
         4. SecurityContext 설정
         ↓
         요청 처리 계속
```

**핵심 포인트:**
- 모든 요청마다 실행 (공개 엔드포인트 제외)
- 유효하지 않거나 만료된 토큰 → SecurityContext 초기화, 요청은 계속 진행
- 토큰 검증 중 데이터베이스 조회 없음 (무상태)
- Principal 타입: `AuthenticatedUser` (userId만 포함)

### 4. 인증된 사용자 접근

컨트롤러와 서비스는 `AuthenticationContext`를 통해 현재 인증된 사용자에 접근합니다:

```java
// 현재 사용자 ID 가져오기
Long userId = AuthenticationContext.getCurrentUserId();

// AuthenticatedUser 객체 가져오기
AuthenticatedUser user = AuthenticationContext.getCurrentUser();

// 인증 여부 확인
boolean authenticated = AuthenticationContext.isAuthenticated();
```

**핵심 포인트:**
- Spring Security 내부에 대한 깔끔한 추상화
- 인증이 없으면 `IllegalStateException` 발생
- JWT 인증 성공 후에만 작동

## 도메인 객체

### TokenPayload (Value Object)

```java
public class TokenPayload {
    private Long userId;
    private Instant issuedAt;
    private Instant expiresAt;

    public boolean isExpired() { ... }
}
```

**규칙:**
- 불변 값 객체
- 토큰 클레임만 포함
- 만료 확인을 위한 도메인 메서드
- Spring 의존성 없음 (DOMAIN_RULES 준수)

### AuthenticatedUser (Value Object)

```java
public class AuthenticatedUser {
    private final Long userId;
}
```

**규칙:**
- `Authentication.getPrincipal()`로 사용
- userId만 포함 (역할, 권한 없음)
- 불변이고 무상태
- 인증 컨텍스트에 대한 명확한 계약

## 보안 설정

### 공개 엔드포인트 (인증 불필요)

- `POST /api/auth/login` - 로그인
- `POST /api/users/register` - 회원가입
- `/h2-console/**` - H2 콘솔 (개발 전용)
- `/actuator/health` - 헬스 체크

### 보호된 엔드포인트 (인증 필요)

그 외 모든 엔드포인트는 `Authorization: Bearer {token}` 헤더에 유효한 JWT 토큰이 필요합니다.

### 세션 관리

- **무상태**: `SessionCreationPolicy.STATELESS`
- 서버 측 세션 저장소 없음
- JWT 토큰이 유일한 인증 증명

## 에러 처리

### 인증 에러

- **유효하지 않은 자격증명**: `ErrorCode.INVALID_CREDENTIALS` (401)
- **비활성 사용자**: `ErrorCode.USER_INACTIVE` (403)
- **유효하지 않은 토큰**: `ErrorCode.INVALID_TOKEN` (401)
- **토큰 만료**: `ErrorCode.INVALID_TOKEN` (401)

### 에러 플로우

```
JwtAuthenticationFilter (유효하지 않은 토큰)
  ↓ catch Exception
  SecurityContext 초기화
  계속 진행 (요청이 컨트롤러에 도달)
  ↓ (엔드포인트가 인증 필요한 경우)
  Spring Security가 접근 거부 (401)
```

**핵심 포인트:**
- 유효하지 않은 토큰으로 요청이 중단되지 않음
- Spring Security가 권한 부여 처리
- 각 실패 유형에 대한 명확한 에러 코드

## 토큰 명세

### JWT 구조

```
Header: {"alg": "HS256"}
Payload: {
  "sub": "{userId}",
  "iat": {timestamp},
  "exp": {timestamp}
}
Signature: HMACSHA256(...)
```

### 설정

```properties
jwt.secret={secret-key}           # HMAC 비밀 키
jwt.expiration=86400000          # 24시간 (밀리초)
```

### 토큰 생명주기

1. **발급**: TokenService가 userId를 subject로 하는 JWT 생성
2. **검증**: TokenService가 서명 및 만료 시간 파싱 및 검증
3. **만료**: 24시간 후 토큰 만료 (아직 갱신 메커니즘 없음)

## 패키지 구조

```
auth/
├── controller/
│   └── AuthController.java          # 로그인 엔드포인트
├── service/
│   ├── AuthService.java              # 로그인 오케스트레이션
│   └── TokenService.java             # 토큰 발급/검증
├── domain/
│   ├── TokenPayload.java             # 토큰 클레임 값 객체
│   └── AuthenticatedUser.java       # Security principal
└── dto/
    ├── LoginRequest.java             # 로그인 입력
    └── LoginResponse.java            # 토큰 포함 로그인 출력

common/
├── filter/
│   └── JwtAuthenticationFilter.java  # 토큰 검증 필터
├── config/
│   └── SecurityConfig.java           # Spring Security 설정
└── util/
    └── AuthenticationContext.java    # 인증 헬퍼
```

## 도메인 간 규칙

### Auth → User

- **허용**: UserRepository를 통한 읽기 전용 접근
- **금지**: 사용자 상태 변경
- **사용**: 로그인 시 자격증명 검증을 위한 사용자 조회 필요

### User → Auth

- **금지**: 직접 의존성 없음
- **분리**: 사용자 회원가입 시 토큰 발급하지 않음
- **이유**: 인증은 사용자 관리와 별개의 관심사

## 향후 개선 사항

아직 구현되지 않은 잠재적 개선 사항:

1. **Refresh Token**: 새 액세스 토큰 획득을 위한 장기 토큰
2. **토큰 폐기**: 로그아웃을 위한 블랙리스트 메커니즘
3. **역할 기반 접근 제어**: AuthenticatedUser에 역할/권한 추가
4. **다중 인증**: 추가 인증 요소
5. **OAuth2 통합**: 소셜 로그인 지원

## 테스트 가이드라인

### 단위 테스트

- TokenService: 토큰 생성, 검증, 만료 테스트
- AuthService: UserRepository와 TokenService 모킹
- AuthenticationContext: 모킹된 SecurityContext로 테스트

### 통합 테스트

- 로그인 플로우: 컨트롤러에서 토큰 발급까지 전체 흐름
- 필터: 토큰 추출, 검증, SecurityContext 설정 테스트
- 보호된 엔드포인트: 유효/유효하지 않음/만료된 토큰으로 테스트

### 보안 테스트

- 비밀번호 검증
- 토큰 변조 감지
- 만료된 토큰 거부
- 비활성 사용자 로그인 방지
