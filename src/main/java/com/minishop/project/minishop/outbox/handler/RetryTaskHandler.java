package com.minishop.project.minishop.outbox.handler;

public interface RetryTaskHandler {

    String taskType();

    void handle(String payload);
}
