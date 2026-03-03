package com.minishop.project.minishop.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderItemRequest {
    @NotNull
    private Long productId;
    @NotNull
    @Positive
    private Long quantity;

    public OrderItemRequest(Long productId, Long quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
}
