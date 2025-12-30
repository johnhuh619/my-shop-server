package com.minishop.project.minishop.payment.event;

import com.minishop.project.minishop.payment.domain.Payment;
import lombok.Getter;

import java.time.Instant;

/**
 * Payment 생성 이벤트
 *
 * Payment 엔티티 생성 후 외부 PG 호출을 비동기로 처리하기 위한 이벤트
 * 트랜잭션 커밋 후 PaymentEventListener에서 실제 결제 처리 수행
 */
@Getter
public class PaymentCreatedEvent {
    private final Long paymentId;
    private final Long userId;
    private final Long orderId;
    private final Long amount;
    private final Instant createdAt;

    private PaymentCreatedEvent(Long paymentId, Long userId, Long orderId,
                                 Long amount, Instant createdAt) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public static PaymentCreatedEvent from(Payment payment) {
        return new PaymentCreatedEvent(
                payment.getId(),
                payment.getUserId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCreatedAt()
        );
    }
}
