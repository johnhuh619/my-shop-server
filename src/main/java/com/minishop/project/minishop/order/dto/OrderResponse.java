package com.minishop.project.minishop.order.dto;

import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
public class OrderResponse {
    private final Long id;
    private final Long userId;
    private final OrderStatus status;
    private final Long totalAmount;
    private final List<OrderItemResponse> items;
    private final Instant createdAt;
    private final Instant updatedAt;

    private OrderResponse(Long id, Long userId, OrderStatus status, Long totalAmount,
                          List<OrderItemResponse> items, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
