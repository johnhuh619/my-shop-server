package com.minishop.project.minishop.order.service;

import com.minishop.project.minishop.common.dto.InternalJobResult;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationJobService {

    private static final long EXPIRATION_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public InternalJobResult expireDueOrders(int limit) {
        List<Long> expiredOrderIds = findExpiredOrderIds(limit);
        int successCount = 0;
        int failureCount = 0;

        for (Long orderId : expiredOrderIds) {
            try {
                orderService.expireOrder(orderId);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to expire order: orderId={}, error={}", orderId, e.getMessage(), e);
            }
        }

        return new InternalJobResult(
                "order-expiration",
                limit,
                expiredOrderIds.size(),
                successCount,
                failureCount
        );
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredOrderIds(int limit) {
        Instant expirationTime = Instant.now().minus(EXPIRATION_MINUTES, ChronoUnit.MINUTES);
        return orderRepository.findIdsByStatusAndCreatedAtBefore(
                OrderStatus.CREATED,
                expirationTime,
                PageRequest.of(0, limit)
        );
    }
}
