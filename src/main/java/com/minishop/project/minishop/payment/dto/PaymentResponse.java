package com.minishop.project.minishop.payment.dto;

import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.domain.PaymentStatus;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PaymentResponse {
    private final Long id;
    private final Long userId;
    private final Long orderId;
    private final PaymentStatus status;
    private final Long amount;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PaymentResponse(Long id, Long userId, Long orderId, PaymentStatus status,
                            Long amount, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getUserId(),
                payment.getOrderId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
