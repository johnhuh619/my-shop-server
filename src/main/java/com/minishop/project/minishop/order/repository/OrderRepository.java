package com.minishop.project.minishop.order.repository;

import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o WHERE o.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    List<Order> findByUserId(Long userId);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
}
