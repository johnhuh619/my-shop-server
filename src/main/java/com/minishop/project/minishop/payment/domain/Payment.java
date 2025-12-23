package com.minishop.project.minishop.payment.domain;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "idempotency_key"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    // === SNAPSHOT FIELDS (IMMUTABLE) ===
    @Column(nullable = false)
    private Long amount;  // Order totalAmount 스냅샷

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private Payment(Long userId, Long orderId, String idempotencyKey,
                    PaymentStatus status, Long amount, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.amount = amount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Payment create(Long userId, Long orderId, String idempotencyKey, Long amount) {
        Instant now = Instant.now();
        return Payment.builder()
                .userId(userId)
                .orderId(orderId)
                .idempotencyKey(idempotencyKey)
                .status(PaymentStatus.REQUESTED)
                .amount(amount)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void markAsCompleted() {
        if (this.status != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Payment can only be completed when status is REQUESTED");
        }
        this.status = PaymentStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void markAsFailed() {
        if (this.status != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Payment can only be failed when status is REQUESTED");
        }
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}
