package com.minishop.project.minishop.outbox.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "retry_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetryTask {

    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final long BASE_BACKOFF_SECONDS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String taskType;

    @Column(nullable = false, length = 1000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RetryTaskStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private int maxRetries;

    @Column(nullable = false)
    private Instant nextRetryAt;

    @Column(length = 2000)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private RetryTask(String taskType, String payload, RetryTaskStatus status,
                      int retryCount, int maxRetries, Instant nextRetryAt,
                      String lastError, Instant createdAt, Instant updatedAt) {
        this.taskType = taskType;
        this.payload = payload;
        this.status = status;
        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
        this.nextRetryAt = nextRetryAt;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RetryTask create(String taskType, String payload) {
        Instant now = Instant.now();
        return RetryTask.builder()
                .taskType(taskType)
                .payload(payload)
                .status(RetryTaskStatus.PENDING)
                .retryCount(0)
                .maxRetries(DEFAULT_MAX_RETRIES)
                .nextRetryAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void markCompleted() {
        this.status = RetryTaskStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void recordFailure(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage;
        this.updatedAt = Instant.now();

        if (this.retryCount >= this.maxRetries) {
            this.status = RetryTaskStatus.EXHAUSTED;
        } else {
            long backoffSeconds = BASE_BACKOFF_SECONDS * (1L << this.retryCount);
            this.nextRetryAt = Instant.now().plusSeconds(backoffSeconds);
        }
    }
}
