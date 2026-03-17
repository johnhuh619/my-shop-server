package com.minishop.project.minishop.outbox.service;

import com.minishop.project.minishop.common.dto.InternalJobResult;
import com.minishop.project.minishop.outbox.domain.RetryTask;
import com.minishop.project.minishop.outbox.domain.RetryTaskStatus;
import com.minishop.project.minishop.outbox.repository.RetryTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryTaskJobService {

    private final RetryTaskRepository retryTaskRepository;
    private final RetryTaskService retryTaskService;

    public InternalJobResult processDueTasks(int limit) {
        List<RetryTask> tasks = findDueTasks(limit);
        int successCount = 0;
        int failureCount = 0;

        if (!tasks.isEmpty()) {
            log.info("RetryTask job: found {} pending tasks", tasks.size());
        }

        for (RetryTask task : tasks) {
            try {
                retryTaskService.processTask(task);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("RetryTask job: unexpected error processing task id={}: {}",
                        task.getId(), e.getMessage(), e);
            }
        }

        return new InternalJobResult(
                "retry-task-batch",
                limit,
                tasks.size(),
                successCount,
                failureCount
        );
    }

    @Transactional(readOnly = true)
    public List<RetryTask> findDueTasks(int limit) {
        return retryTaskRepository.findByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
                RetryTaskStatus.PENDING,
                Instant.now(),
                PageRequest.of(0, limit)
        );
    }
}
