package com.minishop.project.minishop.inventory.domain;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "inventories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private Long quantityAvailable;

    @Column(nullable = false)
    private Long quantityReserved;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    public Inventory(Long id, Long productId, Long quantityAvailable, Long quantityReserved,
                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.productId = productId;
        this.quantityAvailable = quantityAvailable != null ? quantityAvailable : 0L;
        this.quantityReserved = quantityReserved != null ? quantityReserved : 0L;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Inventory create(Long productId, Long initialQuantity) {
        return Inventory.builder()
                .productId(productId)
                .quantityAvailable(initialQuantity)
                .quantityReserved(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /// 예약
    public void reserve(Long quantity) {
        if (quantityAvailable < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY);
        }
        this.quantityAvailable -= quantity;
        this.quantityReserved += quantity;
        this.updatedAt = Instant.now();
    }

    /// 취소 시 롤백
    public void release(Long quantity) {
        if (quantityReserved < quantity) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "Cannot release more than reserved");
        }
        this.quantityReserved -= quantity;
        this.quantityAvailable += quantity;
        this.updatedAt = Instant.now();
    }

    /// reserve -> 실제 반영
    public void confirm(Long quantity) {
        if (quantityReserved < quantity) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "Cannot confirm more than reserved");
        }
        this.quantityReserved -= quantity;
        this.updatedAt = Instant.now();
    }

    public void addStock(Long quantity) {
        this.quantityAvailable += quantity;
        this.updatedAt = Instant.now();
    }

    public Long getTotalQuantity() {
        return quantityAvailable + quantityReserved;
    }
}
