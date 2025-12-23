package com.minishop.project.minishop.refund.dto;

import lombok.Getter;

@Getter
public class CreateRefundRequest {
    private Long paymentId;
    private Long amount;      // 부분 환불 시 금액 지정 (null이면 전액 환불)
    private String reason;    // 환불 사유
}
