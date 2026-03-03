package com.minishop.project.minishop.refund.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class RefundItemRequest {
    @NotNull
    private Long orderItemId;
    @NotNull
    @Positive
    private Long quantity;
}
