package com.minishop.project.minishop.inventory.repository;

import com.minishop.project.minishop.inventory.domain.InventoryOperationLog;
import com.minishop.project.minishop.inventory.domain.InventoryOperationLog.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryOperationLogRepository extends JpaRepository<InventoryOperationLog, Long> {

    boolean existsByOrderIdAndProductIdAndOperationType(
            Long orderId, Long productId, OperationType operationType);
}
