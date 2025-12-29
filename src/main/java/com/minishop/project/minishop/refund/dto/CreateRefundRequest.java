package com.minishop.project.minishop.refund.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class CreateRefundRequest {
    private Long paymentId;
    private List<RefundItemRequest> items;
    private String reason;
}
