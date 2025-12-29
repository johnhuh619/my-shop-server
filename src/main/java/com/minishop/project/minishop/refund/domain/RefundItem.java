package com.minishop.project.minishop.refund.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "refund_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    // === SNAPSHOT FIELDS (IMMUTABLE) ===
    @Column(nullable = false)
    private Long orderItemId;       // 원본 OrderItem ID (참조용)

    @Column(nullable = false)
    private Long productId;         // 재고 복구용

    @Column(nullable = false)
    private String productName;     // 스냅샷

    @Column(nullable = false)
    private Long unitPrice;         // 스냅샷 (OrderItem의 unitPrice)

    @Column(nullable = false)
    private Long quantity;          // 환불 수량

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private RefundItem(Long orderItemId, Long productId, String productName,
                       Long unitPrice, Long quantity, Instant createdAt) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public static RefundItem create(Long orderItemId, Long productId,
                                    String productName, Long unitPrice, Long quantity) {
        return RefundItem.builder()
                .orderItemId(orderItemId)
                .productId(productId)
                .productName(productName)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .createdAt(Instant.now())
                .build();
    }

    void setRefund(Refund refund) {
        this.refund = refund;
    }

    public Long getSubtotal() {
        return this.unitPrice * this.quantity;
    }
}
