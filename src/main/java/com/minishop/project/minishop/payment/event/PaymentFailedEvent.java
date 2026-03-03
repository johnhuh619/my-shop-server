package com.minishop.project.minishop.payment.event;

import com.minishop.project.minishop.payment.domain.Payment;
import lombok.Getter;

import java.time.Instant;

/**
 * Payment 실패 이벤트
 *
 * Spring Event 기반 비동기 처리
 * 결제 실패 시 cancelOrderBySystem을 통해 재고 해제 + 주문 취소
 */
@Getter
public class PaymentFailedEvent {
    private final Long paymentId;
    private final Long userId;
    private final Long orderId;
    private final Long amount;
    private final Instant failedAt;

    private PaymentFailedEvent(Long paymentId, Long userId, Long orderId,
                                Long amount, Instant failedAt) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
        this.failedAt = failedAt;
    }

    public static PaymentFailedEvent from(Payment payment) {
        return new PaymentFailedEvent(
                payment.getId(),
                payment.getUserId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getUpdatedAt()
        );
    }
}
