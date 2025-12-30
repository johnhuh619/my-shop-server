package com.minishop.project.minishop.payment.event;

import com.minishop.project.minishop.order.domain.OrderItem;
import com.minishop.project.minishop.payment.domain.Payment;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Payment 실패 이벤트
 *
 * Spring Event 기반 비동기 처리
 * 결제 실패 시 재고 해제를 위해 OrderItem 정보 포함
 */
@Getter
public class PaymentFailedEvent {
    private final Long paymentId;
    private final Long userId;
    private final Long orderId;
    private final Long amount;
    private final Instant failedAt;
    private final List<OrderItemSnapshot> orderItems;

    private PaymentFailedEvent(Long paymentId, Long userId, Long orderId,
                                Long amount, Instant failedAt, List<OrderItemSnapshot> orderItems) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
        this.failedAt = failedAt;
        this.orderItems = orderItems;
    }

    public static PaymentFailedEvent from(Payment payment, List<OrderItem> orderItems) {
        List<OrderItemSnapshot> snapshots = orderItems.stream()
                .map(OrderItemSnapshot::from)
                .toList();

        return new PaymentFailedEvent(
                payment.getId(),
                payment.getUserId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getUpdatedAt(),
                snapshots
        );
    }

    /**
     * 재고 해제를 위한 OrderItem 스냅샷
     */
    public record OrderItemSnapshot(
            Long productId,
            Long quantity
    ) {
        public static OrderItemSnapshot from(OrderItem orderItem) {
            return new OrderItemSnapshot(
                    orderItem.getProductId(),
                    orderItem.getQuantity()
            );
        }
    }
}
