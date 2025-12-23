package com.minishop.project.minishop.refund.dto;

import com.minishop.project.minishop.refund.domain.Refund;
import com.minishop.project.minishop.refund.domain.RefundStatus;
import lombok.Getter;

import java.time.Instant;

@Getter
public class RefundResponse {
    private final Long id;
    private final Long userId;
    private final Long paymentId;
    private final Long orderId;
    private final RefundStatus status;
    private final Long amount;
    private final String reason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RefundResponse(Long id, Long userId, Long paymentId, Long orderId,
                           RefundStatus status, Long amount, String reason,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getUserId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getStatus(),
                refund.getAmount(),
                refund.getReason(),
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }
}
