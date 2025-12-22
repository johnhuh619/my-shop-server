package com.minishop.project.minishop.inventory.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.domain.Inventory;
import com.minishop.project.minishop.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional
    public Inventory initializeInventory(Long productId) {
        if (inventoryRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "Inventory already exists for product");
        }
        Inventory inventory = Inventory.create(productId, 0L);
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public Inventory addStock(Long productId, Long quantity) {
        validateQuantity(quantity);

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));

        inventory.addStock(quantity);
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public void reserve(Long productId, Long quantity) {
        validateQuantity(quantity);

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));

        inventory.reserve(quantity);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void release(Long productId, Long quantity) {
        validateQuantity(quantity);

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));

        inventory.release(quantity);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void confirm(Long productId, Long quantity) {
        validateQuantity(quantity);

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));

        inventory.confirm(quantity);
        inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public Long getAvailableQuantity(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
        return inventory.getQuantityAvailable();
    }

    @Transactional(readOnly = true)
    public Inventory getByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
    }

    private void validateQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "Quantity must be positive");
        }
    }
}
