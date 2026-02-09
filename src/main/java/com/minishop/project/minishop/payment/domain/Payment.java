package com.minishop.project.minishop.payment.domain;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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

    @Column(nullable = false, unique = true)
    private String tossOrderId;

    @Column
    private String paymentKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private Payment(Long userId, Long orderId, String idempotencyKey,
                    PaymentStatus status, Long amount, String tossOrderId,
                    String paymentKey, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.amount = amount;
        this.tossOrderId = tossOrderId;
        this.paymentKey = paymentKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Payment create(Long userId, Long orderId, String idempotencyKey, Long amount) {
        Instant now = Instant.now();
        String tossOrderId = generateTossOrderId(orderId);
        return Payment.builder()
                .userId(userId)
                .orderId(orderId)
                .idempotencyKey(idempotencyKey)
                .status(PaymentStatus.REQUESTED)
                .amount(amount)
                .tossOrderId(tossOrderId)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void assignPaymentKey(String paymentKey) {
        if (this.status != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Payment key can only be assigned when status is REQUESTED");
        }
        if (this.paymentKey != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT,
                    "Payment key already assigned");
        }
        this.paymentKey = paymentKey;
        this.updatedAt = Instant.now();
    }

    public void validateAmount(Long requestedAmount) {
        if (!this.amount.equals(requestedAmount)) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "Expected " + this.amount + " but got " + requestedAmount);
        }
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

    private static String generateTossOrderId(Long orderId) {
        String uuid8 = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "MINISHOP_" + orderId + "_" + uuid8;
    }
}
