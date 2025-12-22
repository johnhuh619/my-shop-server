package com.minishop.project.minishop.inventory.dto;

import com.minishop.project.minishop.inventory.domain.Inventory;
import lombok.Getter;

import java.time.Instant;

@Getter
public class InventoryResponse {
    private final Long id;
    private final Long productId;
    private final Long quantityAvailable;
    private final Long quantityReserved;
    private final Instant createdAt;
    private final Instant updatedAt;

    private InventoryResponse(Long id, Long productId, Long quantityAvailable,
                             Long quantityReserved, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.productId = productId;
        this.quantityAvailable = quantityAvailable;
        this.quantityReserved = quantityReserved;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getQuantityAvailable(),
                inventory.getQuantityReserved(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt()
        );
    }
}
