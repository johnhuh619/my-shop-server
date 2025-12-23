package com.minishop.project.minishop.payment.event;

import com.minishop.project.minishop.payment.domain.Payment;
import lombok.Getter;

import java.time.Instant;

/**
 * Payment 완료 이벤트
 *
 * 추후 Outbox 패턴 전환 시 사용할 이벤트 클래스
 * 현재는 동기 호출로 구현되어 있지만, 구조는 이벤트 기반으로 설계
 */
@Getter
public class PaymentCompletedEvent {
    private final Long paymentId;
    private final Long userId;
    private final Long orderId;
    private final Long amount;
    private final Instant completedAt;

    private PaymentCompletedEvent(Long paymentId, Long userId, Long orderId,
                                   Long amount, Instant completedAt) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
        this.completedAt = completedAt;
    }

    public static PaymentCompletedEvent from(Payment payment) {
        return new PaymentCompletedEvent(
                payment.getId(),
                payment.getUserId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getUpdatedAt()
        );
    }
}
