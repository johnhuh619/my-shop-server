package com.minishop.project.minishop.refund.domain;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long paymentId;    // Payment 기준으로 환불

    @Column(nullable = false)
    private Long orderId;      // 참조용

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(nullable = false)
    private Long amount;       // 환불 금액 (부분 환불 가능)

    private String reason;     // 환불 사유

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private Refund(Long userId, Long paymentId, Long orderId, RefundStatus status,
                   Long amount, String reason, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Refund create(Long userId, Long paymentId, Long orderId,
                                Long amount, String reason) {
        Instant now = Instant.now();
        return Refund.builder()
                .userId(userId)
                .paymentId(paymentId)
                .orderId(orderId)
                .status(RefundStatus.REQUESTED)
                .amount(amount)
                .reason(reason)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void markAsCompleted() {
        if (this.status != RefundStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Refund can only be completed when status is REQUESTED");
        }
        this.status = RefundStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void markAsFailed() {
        if (this.status != RefundStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Refund can only be failed when status is REQUESTED");
        }
        this.status = RefundStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}
