package com.minishop.project.minishop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class ConfirmPaymentRequest {
    @NotBlank
    private String paymentKey;
    @NotBlank
    private String orderId;
    @NotNull
    @Positive
    private Long amount;
}
