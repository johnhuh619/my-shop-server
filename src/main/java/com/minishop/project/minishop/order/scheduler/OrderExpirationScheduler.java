package com.minishop.project.minishop.order.scheduler;

import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.repository.OrderRepository;
import com.minishop.project.minishop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 주문 만료 스케줄러
 *
 * CREATED 상태에서 30분 이내 결제되지 않은 주문을 자동으로 만료 처리
 * - 재고 예약 해제
 * - 주문 상태를 EXPIRED로 변경
 */
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    private static final long EXPIRATION_MINUTES = 30;

    /**
     * 1분마다 실행
     * 만료된 주문을 찾아서 처리
     */
    @Scheduled(fixedRate = 60000) // 60초 = 1분
    @Transactional
    public void expireOrders() {
        Instant expirationTime = Instant.now().minus(EXPIRATION_MINUTES, ChronoUnit.MINUTES);

        List<Order> expiredOrders = orderRepository
                .findByStatusAndCreatedAtBefore(OrderStatus.CREATED, expirationTime);

        for (Order order : expiredOrders) {
            try {
                orderService.expireOrder(order.getId());
            } catch (Exception e) {
                // 로그 기록 후 계속 진행
                // TODO: 로깅 추가
                System.err.println("Failed to expire order: " + order.getId() + ", error: " + e.getMessage());
            }
        }
    }
}
