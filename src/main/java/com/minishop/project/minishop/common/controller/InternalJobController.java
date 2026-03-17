package com.minishop.project.minishop.common.controller;

import com.minishop.project.minishop.common.dto.InternalJobResult;
import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.order.service.OrderExpirationJobService;
import com.minishop.project.minishop.outbox.service.RetryTaskJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs")
@RequiredArgsConstructor
public class InternalJobController {

    private final OrderExpirationJobService orderExpirationJobService;
    private final RetryTaskJobService retryTaskJobService;

    @Value("${internal.jobs.order-expiration.batch-size:100}")
    private int defaultOrderExpirationBatchSize;

    @Value("${internal.jobs.retry.batch-size:100}")
    private int defaultRetryBatchSize;

    @PostMapping("/orders/expire")
    public ApiResponse<InternalJobResult> expireOrders(@RequestParam(required = false) Integer limit) {
        return ApiResponse.success(orderExpirationJobService.expireDueOrders(resolveLimit(limit, defaultOrderExpirationBatchSize)));
    }

    @PostMapping("/retry-tasks/process")
    public ApiResponse<InternalJobResult> processRetryTasks(@RequestParam(required = false) Integer limit) {
        return ApiResponse.success(retryTaskJobService.processDueTasks(resolveLimit(limit, defaultRetryBatchSize)));
    }

    private int resolveLimit(Integer limit, int defaultLimit) {
        int resolvedLimit = limit == null ? defaultLimit : limit;
        if (resolvedLimit <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "limit must be positive");
        }
        return resolvedLimit;
    }
}
