package com.minishop.project.minishop.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderItemRequest {
    private Long productId;
    private Long quantity;

    public OrderItemRequest(Long productId, Long quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
}
