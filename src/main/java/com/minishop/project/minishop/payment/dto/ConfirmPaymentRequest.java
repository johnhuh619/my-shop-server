package com.minishop.project.minishop.payment.dto;

import lombok.Getter;

@Getter
public class ConfirmPaymentRequest {
    private String paymentKey;
    private String orderId;
    private Long amount;
}
