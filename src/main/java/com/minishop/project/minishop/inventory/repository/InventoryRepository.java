package com.minishop.project.minishop.inventory.repository;

import com.minishop.project.minishop.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inventory> findByProductIdWithLock(@Param("productId") Long productId);

    Optional<Inventory> findByProductId(Long productId);

    boolean existsByProductId(Long productId);
}
