package com.minishop.project.minishop.outbox.repository;

import com.minishop.project.minishop.outbox.domain.RetryTask;
import com.minishop.project.minishop.outbox.domain.RetryTaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RetryTaskRepository extends JpaRepository<RetryTask, Long> {

    List<RetryTask> findByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            RetryTaskStatus status,
            Instant now,
            Pageable pageable
    );

    List<RetryTask> findByStatusAndNextRetryAtBefore(RetryTaskStatus status, Instant now);

    Optional<RetryTask> findByTaskTypeAndPayloadAndStatus(String taskType, String payload, RetryTaskStatus status);
}
