package com.minishop.project.minishop.common.dto;

public record InternalJobResult(
        String jobName,
        int requestedLimit,
        int selectedCount,
        int successCount,
        int failureCount
) {
}
