package com.minishop.project.minishop.order.scheduler;

import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.repository.OrderRepository;
import com.minishop.project.minishop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 *
 * 설계 결정:
 * 1. expireOrders()에 @Transactional을 걸면 안 된다.
 *    목록 조회로 L1 캐시에 로드된 엔티티가 expireOrder() 내부의
 *    PESSIMISTIC_WRITE 락 조회 결과를 덮어쓰기 때문에,
 *    결제 완료된 주문이 stale 상태(CREATED)로 잘못 만료될 수 있다.
 *    → 목록 조회와 만료 처리를 별도 트랜잭션으로 분리.
 *    (상세: docs/JPA_L1_CACHE_TROUBLESHOOTING.md Case 1)
 *
 * 2. ID만 조회하여 엔티티 로드를 피한다.
 *    findIdsByStatusAndCreatedAtBefore()는 JPQL projection으로
 *    Order 엔티티를 영속성 컨텍스트에 올리지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "internal.jobs.scheduler.enabled", havingValue = "true", matchIfMissing = true)
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
    public void expireOrders() {
        List<Long> expiredOrderIds = findExpiredOrderIds();

        for (Long orderId : expiredOrderIds) {
            try {
                orderService.expireOrder(orderId);
            } catch (Exception e) {
                log.error("Failed to expire order: orderId={}, error={}", orderId, e.getMessage(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredOrderIds() {
        Instant expirationTime = Instant.now().minus(EXPIRATION_MINUTES, ChronoUnit.MINUTES);
        return orderRepository.findIdsByStatusAndCreatedAtBefore(OrderStatus.CREATED, expirationTime);
    }
}
