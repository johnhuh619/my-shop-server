package com.minishop.project.minishop.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "inventory_operation_logs",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_inv_op_log",
           columnNames = {"orderId", "productId", "operationType"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperationType operationType;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public enum OperationType {
        CONFIRM,
        RELEASE
    }

    private InventoryOperationLog(Long orderId, Long productId,
                                   OperationType operationType, Long quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.operationType = operationType;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public static InventoryOperationLog create(Long orderId, Long productId,
                                                OperationType operationType, Long quantity) {
        return new InventoryOperationLog(orderId, productId, operationType, quantity);
    }
}
