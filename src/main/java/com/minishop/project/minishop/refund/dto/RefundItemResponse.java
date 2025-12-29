package com.minishop.project.minishop.refund.dto;

import com.minishop.project.minishop.refund.domain.RefundItem;
import lombok.Getter;

@Getter
public class RefundItemResponse {

    private final Long id;
    private final Long orderItemId;
    private final Long productId;
    private final String productName;
    private final Long unitPrice;
    private final Long quantity;
    private final Long subtotal;

    private RefundItemResponse(Long id, Long orderItemId, Long productId,
                               String productName, Long unitPrice, Long quantity) {
        this.id = id;
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.subtotal = unitPrice * quantity;
    }

    public static RefundItemResponse from(RefundItem item) {
        return new RefundItemResponse(
                item.getId(),
                item.getOrderItemId(),
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity()
        );
    }
}
