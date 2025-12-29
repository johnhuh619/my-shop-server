package com.minishop.project.minishop.refund.dto;

import lombok.Getter;

@Getter
public class RefundItemRequest {
    private Long orderItemId;
    private Long quantity;
}
