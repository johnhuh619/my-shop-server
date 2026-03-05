package com.minishop.project.minishop.inventory.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.domain.Inventory;
import com.minishop.project.minishop.inventory.repository.InventoryRepository;
import com.minishop.project.minishop.inventory.domain.InventoryOperationLog;
import com.minishop.project.minishop.inventory.domain.InventoryOperationLog.OperationType;
import com.minishop.project.minishop.inventory.repository.InventoryOperationLogRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryOperationLogRepository operationLogRepository;

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmForOrder(Long orderId, Long productId, Long quantity) {
        validateQuantity(quantity);

        if (operationLogRepository.existsByOrderIdAndProductIdAndOperationType(
                orderId, productId, OperationType.CONFIRM)) {
            return;
        }

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));

        inventory.confirm(quantity);
        inventoryRepository.save(inventory);
        operationLogRepository.save(
                InventoryOperationLog.create(orderId, productId, OperationType.CONFIRM, quantity));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseForOrder(Long orderId, Long productId, Long quantity) {
        validateQuantity(quantity);

        if (operationLogRepository.existsByOrderIdAndProductIdAndOperationType(
                orderId, productId, OperationType.RELEASE)) {
            return;
        }

        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));

        inventory.release(quantity);
        inventoryRepository.save(inventory);
        operationLogRepository.save(
                InventoryOperationLog.create(orderId, productId, OperationType.RELEASE, quantity));
    }

    @Transactional(readOnly = true)
    public Long getAvailableQuantity(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
        return inventory.getQuantityAvailable();
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> getAvailableQuantities(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(
                        Inventory::getProductId,
                        Inventory::getQuantityAvailable
                ));
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
