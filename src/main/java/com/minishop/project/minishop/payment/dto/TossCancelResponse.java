package com.minishop.project.minishop.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossCancelResponse {
    private String paymentKey;
    private String orderId;
    private String status;
    private Long totalAmount;
    private Long balanceAmount;
    private Long cancelAmount;
    private String cancelReason;
    private String canceledAt;
}
