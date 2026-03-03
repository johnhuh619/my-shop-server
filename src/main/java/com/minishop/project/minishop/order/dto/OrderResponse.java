package com.minishop.project.minishop.order.dto;

import com.minishop.project.minishop.delivery.domain.DeliveryStatus;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Getter
public class OrderResponse {
    private final Long id;
    private final Long userId;
    private final OrderStatus status;
    private final Long totalAmount;
    private final List<OrderItemResponse> items;
    private final DeliveryStatus deliveryStatus;
    private final Instant createdAt;
    private final Instant updatedAt;

    private OrderResponse(Long id, Long userId, OrderStatus status, Long totalAmount,
                          List<OrderItemResponse> items,
                          DeliveryStatus deliveryStatus,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
        this.deliveryStatus = deliveryStatus;
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
                null,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public static OrderResponse from(Order order, Set<Long> refundedItemIds) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.from(item, refundedItemIds.contains(item.getId())))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                itemResponses,
                null,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public static OrderResponse from(Order order, Set<Long> refundedItemIds,
                                     DeliveryStatus deliveryStatus) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.from(item, refundedItemIds.contains(item.getId())))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                itemResponses,
                deliveryStatus,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
