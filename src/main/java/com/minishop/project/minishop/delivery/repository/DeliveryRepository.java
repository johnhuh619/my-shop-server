package com.minishop.project.minishop.delivery.repository;

import com.minishop.project.minishop.delivery.domain.Delivery;
import com.minishop.project.minishop.delivery.domain.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderId(Long orderId);

    Optional<Delivery> findByIdAndUserId(Long id, Long userId);

    Optional<Delivery> findByOrderIdAndUserId(Long orderId, Long userId);

    List<Delivery> findByUserId(Long userId);

    List<Delivery> findByStatus(DeliveryStatus status);
}
