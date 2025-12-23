package com.minishop.project.minishop.order.dto;

import com.minishop.project.minishop.order.domain.OrderItem;
import lombok.Getter;

@Getter
public class OrderItemResponse {
    private final Long id;
    private final Long productId;
    private final String productName;
    private final Long unitPrice;
    private final Long quantity;
    private final Long subtotal;

    private OrderItemResponse(Long id, Long productId, String productName,
                              Long unitPrice, Long quantity, Long subtotal) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.subtotal = subtotal;
    }

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }
}
