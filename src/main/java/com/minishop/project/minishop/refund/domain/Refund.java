package com.minishop.project.minishop.refund.domain;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    private Long amount;       // 환불 금액 (RefundItem 합계로 자동 계산)

    private String reason;     // 환불 사유

    private String adminComment;  // 관리자 코멘트 (승인/거절 시)

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefundItem> refundItems = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private Refund(Long userId, Long paymentId, Long orderId, RefundStatus status,
                   Long amount, String reason, String adminComment,
                   Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.reason = reason;
        this.adminComment = adminComment;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Refund create(Long userId, Long paymentId, Long orderId,
                                List<RefundItem> items, String reason) {
        Instant now = Instant.now();
        Refund refund = Refund.builder()
                .userId(userId)
                .paymentId(paymentId)
                .orderId(orderId)
                .status(RefundStatus.REQUESTED)
                .amount(0L)
                .reason(reason)
                .createdAt(now)
                .updatedAt(now)
                .build();

        for (RefundItem item : items) {
            refund.addRefundItem(item);
        }
        refund.calculateAmount();

        return refund;
    }

    private void addRefundItem(RefundItem item) {
        this.refundItems.add(item);
        item.setRefund(this);
    }

    private void calculateAmount() {
        this.amount = refundItems.stream()
                .mapToLong(RefundItem::getSubtotal)
                .sum();
    }

    public void approve(String comment) {
        if (this.status != RefundStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATUS,
                    "Refund can only be approved when status is REQUESTED");
        }
        this.status = RefundStatus.APPROVED;
        this.adminComment = comment;
        this.updatedAt = Instant.now();
    }

    public void reject(String comment) {
        if (this.status != RefundStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATUS,
                    "Refund can only be rejected when status is REQUESTED");
        }
        this.status = RefundStatus.REJECTED;
        this.adminComment = comment;
        this.updatedAt = Instant.now();
    }

    public void markAsCompleted() {
        if (this.status != RefundStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATUS,
                    "Refund can only be completed when status is APPROVED");
        }
        this.status = RefundStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void markAsFailed() {
        if (this.status != RefundStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATUS,
                    "Refund can only be failed when status is APPROVED");
        }
        this.status = RefundStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}
