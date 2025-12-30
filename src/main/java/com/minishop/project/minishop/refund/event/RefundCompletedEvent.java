package com.minishop.project.minishop.refund.event;

import com.minishop.project.minishop.refund.domain.Refund;
import com.minishop.project.minishop.refund.domain.RefundItem;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Refund 완료 이벤트
 *
 * Spring Event 기반 비동기 처리
 * 환불 완료 시 Order 상태 변경 및 재고 복구를 위해 RefundItem 정보 포함
 */
@Getter
public class RefundCompletedEvent {
    private final Long refundId;
    private final Long userId;
    private final Long paymentId;
    private final Long orderId;
    private final Long amount;
    private final Instant completedAt;
    private final List<RefundItemSnapshot> refundItems;
    private final Long totalRefundedAmount;  // 전액 환불 판단용 (이번 환불 포함)
    private final Long paymentAmount;        // 전액 환불 판단용 (결제 총액)

    private RefundCompletedEvent(Long refundId, Long userId, Long paymentId,
                                  Long orderId, Long amount, Instant completedAt,
                                  List<RefundItemSnapshot> refundItems,
                                  Long totalRefundedAmount, Long paymentAmount) {
        this.refundId = refundId;
        this.userId = userId;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.completedAt = completedAt;
        this.refundItems = refundItems;
        this.totalRefundedAmount = totalRefundedAmount;
        this.paymentAmount = paymentAmount;
    }

    public static RefundCompletedEvent from(Refund refund, Long totalRefundedAmount, Long paymentAmount) {
        List<RefundItemSnapshot> snapshots = refund.getRefundItems().stream()
                .map(RefundItemSnapshot::from)
                .toList();

        return new RefundCompletedEvent(
                refund.getId(),
                refund.getUserId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getAmount(),
                refund.getUpdatedAt(),
                snapshots,
                totalRefundedAmount,
                paymentAmount
        );
    }

    /**
     * 재고 복구를 위한 RefundItem 스냅샷
     */
    public record RefundItemSnapshot(
            Long productId,
            Long quantity
    ) {
        public static RefundItemSnapshot from(RefundItem refundItem) {
            return new RefundItemSnapshot(
                    refundItem.getProductId(),
                    refundItem.getQuantity()
            );
        }
    }
}

