# AsyncConfig 스레드 풀 설정 가이드

## 개요

Spring Event 비동기 처리를 위한 ThreadPoolTaskExecutor 설정과 튜닝 가이드입니다.

## 현재 설정값

**파일**: `src/main/java/com/minishop/project/minishop/common/config/AsyncConfig.java`

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);        // 기본 스레드 수
        executor.setMaxPoolSize(10);        // 최대 스레드 수
        executor.setQueueCapacity(25);      // 대기 큐 크기
        executor.setThreadNamePrefix("event-");
        executor.initialize();
        return executor;
    }
}
```

---

## ThreadPoolTaskExecutor 동작 방식

### 요청 처리 순서

```
1. 현재 스레드 수 < corePoolSize (5)
   → 새 스레드 생성하여 즉시 처리

2. 현재 스레드 수 >= corePoolSize (5)
   → 큐에 적재 (최대 25개)

3. 큐가 가득 찼고 && 현재 스레드 수 < maxPoolSize (10)
   → 추가 스레드 생성 (5개 → 10개)

4. 큐 가득 찼고 && 스레드 수 = maxPoolSize (10)
   → RejectedExecutionException 발생
```

### 시각적 표현

```
┌─────────────────────────────────────────────────────┐
│ 요청 발생                                            │
└──────────────┬──────────────────────────────────────┘
               │
               ▼
       ┌───────────────┐
       │ 스레드 수 < 5? │────YES──→ [새 스레드 생성]
       └───────┬───────┘
               │ NO
               ▼
       ┌───────────────┐
       │ 큐 < 25개?     │────YES──→ [큐에 대기]
       └───────┬───────┘
               │ NO
               ▼
       ┌───────────────┐
       │ 스레드 수 < 10?│────YES──→ [추가 스레드 생성]
       └───────┬───────┘
               │ NO
               ▼
       [RejectedExecutionException]
```

---

## 현재 설정의 의미

### CorePoolSize = 5

- **의미**: "평상시 5개의 이벤트를 동시 처리"
- **처리량**: PG 호출이 3초 소요되므로, 초당 약 1.6개 이벤트 처리 가능
  - 계산: 5개 스레드 × (1/3초) = 약 1.6 TPS

### MaxPoolSize = 10

- **의미**: "최대 10개의 이벤트를 동시 처리"
- **처리량**: 큐가 가득 찼을 때만 추가 스레드 생성
  - 계산: 10개 스레드 × (1/3초) = 약 3.3 TPS

### QueueCapacity = 25

- **의미**: "대기 중인 이벤트 25개까지 버퍼링"
- **효과**: 버스트 트래픽 흡수 가능
- **최악의 대기 시간**: 25 ÷ (5 ÷ 3초) = 약 15초
- **한계**: 버퍼가 가득 차면 요청 거부

---

## 설정값의 근거

### 현재 값의 성격

**현재 값은 "합리적인 기본값"**으로 설정되었으며, 실제로는 다음 요소를 고려하여 튜닝해야 합니다.

### 고려해야 할 요소

#### 1. 예상 트래픽

```
예시 1) 시간당 100건 결제 = 초당 0.028건
→ CorePoolSize = 1~2로도 충분

예시 2) 시간당 10,000건 결제 = 초당 2.8건
→ CorePoolSize = 10 이상 필요
```

#### 2. 작업 소요 시간

```
- PG 호출: 3초 (장시간)
- Order 상태 업데이트: 0.1초 (짧음)
- 재고 해제: 0.1초 (짧음)

결론:
→ PG 호출 이벤트가 병목
→ PG 타임아웃/재시도 정책도 중요
```

#### 3. 서버 리소스

```
CPU 코어 수: 4코어 → 8~16 스레드 권장
메모리: 스레드당 약 1MB → 큰 문제 없음
DB 커넥션 풀: HikariCP 기본 10개
  → 동시 DB 작업 고려 필요
```

#### 4. 지연 시간 허용치

```
큐 25개 + 스레드 5개 처리 중
→ 최악의 경우 대기 시간: 25 ÷ (5 ÷ 3초) = 15초

허용 가능 여부:
- 결제 결과는 폴링/웹훅으로 확인하므로 OK
- 30초 넘으면 사용자 불안 증가
```

---

## 적절한 설정값 계산

### 공식

```
필요한 스레드 수 = (목표 TPS) × (작업 소요 시간)

예) 초당 5건 처리, PG 호출 3초
→ 5 × 3 = 15 스레드 필요
```

### 권장 설정 (트래픽별)

| 트래픽 규모 | CorePool | MaxPool | Queue | 최대 처리량 | 비고 |
|------------|----------|---------|-------|-----------|------|
| 소규모 (TPS < 1) | 2 | 5 | 10 | ~1.6 TPS | 스타트업, MVP |
| 중규모 (TPS 1~5) | 5 | 10 | 25 | ~3.3 TPS | **현재 설정** |
| 대규모 (TPS 5~10) | 15 | 30 | 50 | ~10 TPS | 성장기 |
| 초대규모 (TPS 10+) | 메시지 큐 전환 검토 | | | | Kafka, RabbitMQ |

---

## 현재 설정의 한계

### 최악의 시나리오

```
상황:
- 35개 이벤트 동시 발생 (10개 처리 중 + 25개 큐 대기)
- 36번째 이벤트 → RejectedExecutionException 발생

영향:
- 결제 이벤트 손실 (Payment는 REQUESTED 상태로 남음)
- 수동 복구 필요
```

### 해결책

#### 1. CallerRunsPolicy 적용 (단기)

```java
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
```

- 거부된 작업을 호출한 스레드가 직접 처리
- 이벤트 손실 방지
- 단점: API 응답 시간 증가 (3초)

#### 2. 큐 크기 증가 (중기)

```java
executor.setQueueCapacity(100);  // 25 → 100
```

- 더 많은 버스트 트래픽 흡수
- 단점: 메모리 사용량 증가, 최대 대기 시간 증가

#### 3. 스레드 수 증가 (중기)

```java
executor.setCorePoolSize(15);
executor.setMaxPoolSize(30);
```

- 처리량 증가
- 단점: CPU/DB 커넥션 경합 가능

#### 4. 메시지 큐 전환 (장기)

```
Spring Event → Kafka/RabbitMQ
- 무제한 버퍼링
- 재시도/DLQ 지원
- 분산 처리 가능
```

---

## 실전 권장 사항

### 1. 모니터링 추가

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("event-");

        // 거부 정책: 호출한 스레드가 직접 실행
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 우아한 종료: 대기 중인 작업 완료 후 종료
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
```

### 2. 메트릭 수집

```java
@Component
@RequiredArgsConstructor
public class ThreadPoolMonitor {

    private final ThreadPoolTaskExecutor executor;

    @Scheduled(fixedRate = 60000) // 1분마다
    public void logMetrics() {
        ThreadPoolExecutor threadPool = executor.getThreadPoolExecutor();

        log.info("ThreadPool Metrics: " +
                "active={}, " +
                "poolSize={}, " +
                "queueSize={}, " +
                "completedTasks={}, " +
                "rejectedCount={}",
                threadPool.getActiveCount(),
                threadPool.getPoolSize(),
                threadPool.getQueue().size(),
                threadPool.getCompletedTaskCount(),
                // 커스텀 카운터 필요
        );
    }
}
```

### 3. 부하 테스트

```bash
# JMeter 시나리오
1. 정상 부하: 초당 2건 × 10분
   → 큐 대기 시간, 거부 발생 확인

2. 버스트 부하: 초당 10건 × 1분
   → 최대 큐 크기, 스레드 생성 패턴 확인

3. 지속 과부하: 초당 5건 × 1시간
   → 메모리 누수, CPU 사용률 확인
```

### 4. 단계별 튜닝

```
Phase 1: 모니터링 (현재 설정 유지)
→ 실제 TPS, 큐 대기 시간, 거부 횟수 측정

Phase 2: 설정 조정
→ 메트릭 기반으로 CorePool, Queue 조정

Phase 3: 부하 테스트
→ 피크 트래픽 대응 확인

Phase 4: 알림 설정
→ 큐 > 20개, 거부 발생 시 Slack 알림
```

---

## 트러블슈팅

### 문제 1: RejectedExecutionException 발생

**증상**
```
ERROR: Task rejected from java.util.concurrent.ThreadPoolExecutor
```

**원인**
- 큐가 가득 찼고 최대 스레드 수 도달

**해결**
1. 즉시: CallerRunsPolicy 적용
2. 단기: Queue 크기 증가 (25 → 50)
3. 중기: 스레드 수 증가 (10 → 20)
4. 장기: 메시지 큐 전환

### 문제 2: 메모리 부족

**증상**
```
OutOfMemoryError: unable to create new native thread
```

**원인**
- MaxPoolSize가 너무 큼
- 스레드당 메모리 과다 사용

**해결**
1. MaxPoolSize 축소
2. JVM 힙 크기 증가 (`-Xmx2g`)
3. 스레드 스택 크기 조정 (`-Xss256k`)

### 문제 3: DB 커넥션 부족

**증상**
```
HikariPool: Connection is not available
```

**원인**
- 스레드 수 > DB 커넥션 풀 크기

**해결**
```properties
# application.properties
spring.datasource.hikari.maximum-pool-size=20  # 기본 10 → 20
```

---

## 체크리스트

### 배포 전 확인

- [ ] 예상 TPS 계산 완료
- [ ] 스레드 풀 설정값 검증
- [ ] CallerRunsPolicy 적용
- [ ] DB 커넥션 풀 크기 확인
- [ ] 부하 테스트 완료
- [ ] 모니터링 대시보드 구축
- [ ] 알림 설정 (큐 임계값, 거부 발생)

### 운영 중 모니터링

- [ ] 시간당 TPS 추이
- [ ] 평균/최대 큐 대기 시간
- [ ] 거부된 작업 수
- [ ] 활성 스레드 수
- [ ] CPU/메모리 사용률
- [ ] DB 커넥션 풀 사용률

---

## 참고 자료

### 관련 문서
- [Spring Event 구현 계획](./SPRING_EVENT_PLAN.md)
- [Payment 비동기 처리 플로우](./PAYMENT_ASYNC_FLOW.md)

### 외부 링크
- [Spring @Async 공식 문서](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async)
- [ThreadPoolExecutor 튜닝 가이드](https://www.baeldung.com/java-threadpooltaskexecutor-core-vs-max-poolsize)
- [Little's Law (대기 이론)](https://en.wikipedia.org/wiki/Little%27s_law)

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 | 작성자 |
|------|------|-----------|--------|
| 2025-12-30 | 1.0.0 | 초안 작성 | Claude |

---

**현재 설정(5/10/25)은 중소규모 서비스의 "안전한 기본값"이지만, 실제 트래픽 패턴과 PG 응답 시간을 모니터링하여 조정해야 합니다.**
