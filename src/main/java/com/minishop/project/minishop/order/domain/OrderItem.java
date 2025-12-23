package com.minishop.project.minishop.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long unitPrice;

    @Column(nullable = false)
    private Long quantity;

    @Builder
    public OrderItem(Long id, Order order, Long productId,
                     String productName, Long unitPrice, Long quantity) {
        this.id = id;
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItem create(Long productId, String productName,
                                   Long unitPrice, Long quantity) {
        return OrderItem.builder()
                .productId(productId)
                .productName(productName)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .build();
    }

    void setOrder(Order order) {
        this.order = order;
    }

    public Long getSubtotal() {
        return this.unitPrice * this.quantity;
    }
}
