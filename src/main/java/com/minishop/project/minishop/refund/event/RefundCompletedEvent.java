package com.minishop.project.minishop.refund.event;

import com.minishop.project.minishop.refund.domain.Refund;
import lombok.Getter;

import java.time.Instant;

/**
 * Refund 완료 이벤트
 *
 * 추후 Outbox 패턴 전환 시 사용할 이벤트 클래스
 * 현재는 동기 호출로 구현되어 있지만, 구조는 이벤트 기반으로 설계
 */
@Getter
public class RefundCompletedEvent {
    private final Long refundId;
    private final Long userId;
    private final Long paymentId;
    private final Long orderId;
    private final Long amount;
    private final Instant completedAt;

    private RefundCompletedEvent(Long refundId, Long userId, Long paymentId,
                                  Long orderId, Long amount, Instant completedAt) {
        this.refundId = refundId;
        this.userId = userId;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.completedAt = completedAt;
    }

    public static RefundCompletedEvent from(Refund refund) {
        return new RefundCompletedEvent(
                refund.getId(),
                refund.getUserId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getAmount(),
                refund.getUpdatedAt()
        );
    }
}
