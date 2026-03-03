package com.minishop.project.minishop.refund.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class CreateRefundRequest {
    @NotNull
    private Long paymentId;
    @NotEmpty
    @Valid
    private List<RefundItemRequest> items;
    private String reason;
}
