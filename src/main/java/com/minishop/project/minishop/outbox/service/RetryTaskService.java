package com.minishop.project.minishop.outbox.service;

import com.minishop.project.minishop.outbox.domain.RetryTask;
import com.minishop.project.minishop.outbox.domain.RetryTaskStatus;
import com.minishop.project.minishop.outbox.handler.RetryTaskHandler;
import com.minishop.project.minishop.outbox.repository.RetryTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RetryTaskService {

    private final RetryTaskRepository retryTaskRepository;
    private final Map<String, RetryTaskHandler> handlerMap;

    public RetryTaskService(RetryTaskRepository retryTaskRepository,
                            List<RetryTaskHandler> handlers) {
        this.retryTaskRepository = retryTaskRepository;
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(RetryTaskHandler::taskType, Function.identity()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(String taskType, String payload) {
        boolean exists = retryTaskRepository
                .findByTaskTypeAndPayloadAndStatus(taskType, payload, RetryTaskStatus.PENDING)
                .isPresent();

        if (exists) {
            log.debug("RetryTask already exists: taskType={}, payload={}", taskType, payload);
            return;
        }

        RetryTask task = RetryTask.create(taskType, payload);
        retryTaskRepository.save(task);
        log.info("RetryTask registered: taskType={}, payload={}", taskType, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTask(RetryTask task) {
        RetryTaskHandler handler = handlerMap.get(task.getTaskType());
        if (handler == null) {
            log.error("No handler found for taskType={}, marking as EXHAUSTED", task.getTaskType());
            task.recordFailure("No handler registered for taskType: " + task.getTaskType());
            task.markCompleted();
            retryTaskRepository.save(task);
            return;
        }

        try {
            handler.handle(task.getPayload());
            task.markCompleted();
            log.info("RetryTask completed: id={}, taskType={}", task.getId(), task.getTaskType());
        } catch (Exception e) {
            task.recordFailure(e.getMessage());
            if (task.getStatus() == RetryTaskStatus.EXHAUSTED) {
                log.error("RetryTask EXHAUSTED after {} retries: id={}, taskType={}, lastError={}",
                        task.getRetryCount(), task.getId(), task.getTaskType(), e.getMessage());
            } else {
                log.warn("RetryTask failed, will retry: id={}, taskType={}, retryCount={}, nextRetryAt={}",
                        task.getId(), task.getTaskType(), task.getRetryCount(), task.getNextRetryAt());
            }
        }
        retryTaskRepository.save(task);
    }
}
