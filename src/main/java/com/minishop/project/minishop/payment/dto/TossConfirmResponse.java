package com.minishop.project.minishop.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossConfirmResponse {
    private String paymentKey;
    private String orderId;
    private String status;
    private Long totalAmount;
    private String method;
    private String approvedAt;
}
