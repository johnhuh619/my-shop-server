package com.minishop.project.minishop.outbox.scheduler;

import com.minishop.project.minishop.outbox.domain.RetryTask;
import com.minishop.project.minishop.outbox.domain.RetryTaskStatus;
import com.minishop.project.minishop.outbox.repository.RetryTaskRepository;
import com.minishop.project.minishop.outbox.service.RetryTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "internal.jobs.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RetryTaskScheduler {

    private final RetryTaskRepository retryTaskRepository;
    private final RetryTaskService retryTaskService;

    @Scheduled(fixedRate = 30000)
    public void processRetryTasks() {
        List<RetryTask> tasks = retryTaskRepository
                .findByStatusAndNextRetryAtBefore(RetryTaskStatus.PENDING, Instant.now());

        if (!tasks.isEmpty()) {
            log.info("RetryTaskScheduler: found {} pending tasks", tasks.size());
        }

        for (RetryTask task : tasks) {
            try {
                retryTaskService.processTask(task);
            } catch (Exception e) {
                log.error("RetryTaskScheduler: unexpected error processing task id={}: {}",
                        task.getId(), e.getMessage(), e);
            }
        }
    }
}
