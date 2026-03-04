package com.minishop.project.minishop.order.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles short, isolated transactions for order status transitions.
 * Each method acquires Order FOR UPDATE, changes status, and commits quickly (~2ms).
 * Returns null when the transition is skipped (already in target or compatible state).
 */
@Service
@RequiredArgsConstructor
class OrderStatusTransitioner {

    private final OrderRepository orderRepository;

    @Transactional
    Order markAsPaidStatus(Long orderId) {
        Order order = orderRepository.findByIdWithItemsAndLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.COMPLETED
                || order.getStatus() == OrderStatus.REFUND_REQUESTED
                || order.getStatus() == OrderStatus.REFUNDED) {
            return null;
        }

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Order can only be marked as paid when status is CREATED");
        }

        order.markAsPaid();
        return orderRepository.save(order);
    }

    @Transactional
    Order expireStatus(Long orderId) {
        Order order = orderRepository.findByIdWithItemsAndLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.CREATED) {
            return null;
        }

        order.expire();
        return orderRepository.save(order);
    }

    @Transactional
    Order cancelBySystemStatus(Long orderId) {
        Order order = orderRepository.findByIdWithItemsAndLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.CREATED) {
            return null;
        }

        order.cancel();
        return orderRepository.save(order);
    }
}
