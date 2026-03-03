package com.minishop.project.minishop.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class CreatePaymentRequest {
    @NotNull
    private Long orderId;
}
