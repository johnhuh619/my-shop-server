package com.minishop.project.minishop.refund.dto;

import com.minishop.project.minishop.refund.domain.Refund;
import com.minishop.project.minishop.refund.domain.RefundStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
public class RefundResponse {

    private final Long id;
    private final Long userId;
    private final Long paymentId;
    private final Long orderId;
    private final RefundStatus status;
    private final Long amount;
    private final String reason;
    private final String adminComment;
    private final List<RefundItemResponse> items;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RefundResponse(Long id, Long userId, Long paymentId, Long orderId,
                           RefundStatus status, Long amount, String reason,
                           String adminComment, List<RefundItemResponse> items,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.reason = reason;
        this.adminComment = adminComment;
        this.items = items;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RefundResponse from(Refund refund) {
        List<RefundItemResponse> itemResponses = refund.getRefundItems().stream()
                .map(RefundItemResponse::from)
                .toList();

        return new RefundResponse(
                refund.getId(),
                refund.getUserId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getStatus(),
                refund.getAmount(),
                refund.getReason(),
                refund.getAdminComment(),
                itemResponses,
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }
}
