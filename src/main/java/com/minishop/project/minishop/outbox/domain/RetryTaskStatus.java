package com.minishop.project.minishop.outbox.domain;

public enum RetryTaskStatus {
    PENDING,
    COMPLETED,
    EXHAUSTED
}
