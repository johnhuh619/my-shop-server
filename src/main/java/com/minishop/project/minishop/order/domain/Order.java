package com.minishop.project.minishop.order.domain;

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
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Long totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    public Order(Long id, Long userId, OrderStatus status, Long totalAmount,
                 List<OrderItem> orderItems, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status != null ? status : OrderStatus.CREATED;
        this.totalAmount = totalAmount != null ? totalAmount : 0L;
        this.orderItems = orderItems != null ? orderItems : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Order create(Long userId, List<OrderItem> items) {
        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.CREATED)
                .totalAmount(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        for (OrderItem item : items) {
            order.addOrderItem(item);
        }
        order.calculateTotalAmount();

        return order;
    }

    public void addOrderItem(OrderItem item) {
        this.orderItems.add(item);
        item.setOrder(this);
    }

    private void calculateTotalAmount() {
        this.totalAmount = orderItems.stream()
                .mapToLong(OrderItem::getSubtotal)
                .sum();
    }

    public void cancel() {
        if (this.status != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Order can only be canceled when status is CREATED");
        }
        this.status = OrderStatus.CANCELED;
        this.updatedAt = Instant.now();
    }

    public void markAsPaid() {
        if (this.status != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Order can only be marked as paid when status is CREATED");
        }
        this.status = OrderStatus.PAID;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (this.status != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Order can only be completed when status is PAID");
        }
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        if (this.status != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Order can only be expired when status is CREATED");
        }
        this.status = OrderStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public void requestRefund() {
        if (this.status != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Refund can only be requested when order status is PAID");
        }
        this.status = OrderStatus.REFUND_REQUESTED;
        this.updatedAt = Instant.now();
    }

    public void markAsRefunded() {
        if (this.status != OrderStatus.REFUND_REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Order can only be marked as refunded when status is REFUND_REQUESTED");
        }
        this.status = OrderStatus.REFUNDED;
        this.updatedAt = Instant.now();
    }
}
