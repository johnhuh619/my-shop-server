package com.minishop.project.minishop.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CreateOrderRequest {
    private List<OrderItemRequest> items;

    public CreateOrderRequest(List<OrderItemRequest> items) {
        this.items = items;
    }
}
