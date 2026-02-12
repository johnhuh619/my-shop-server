package com.minishop.project.minishop.payment.event;

import com.minishop.project.minishop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

/**
 * Payment 이벤트 리스너
 *
 * confirm 이후 후속 처리를 비동기로 수행
 * - PaymentCompletedEvent: Order 상태 변경 (CREATED → PAID)
 * - PaymentFailedEvent: 재고 해제 + 주문 취소
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderService orderService;

    /**
     * 결제 완료 이벤트 처리
     * - Order 상태를 CREATED → PAID로 변경
     */
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[{}] Processing PaymentCompletedEvent: orderId={}, paymentId={}",
                Thread.currentThread().getName(), event.getOrderId(), event.getPaymentId());

        try {
            orderService.markAsPaid(event.getOrderId());
            log.info("Order status updated to PAID: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to handle PaymentCompletedEvent: orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * 결제 실패 이벤트 처리
     * - 재고 해제 + 주문 취소 (cancelOrderBySystem이 원자적으로 처리)
     */
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("[{}] Processing PaymentFailedEvent: orderId={}, paymentId={}",
                Thread.currentThread().getName(), event.getOrderId(), event.getPaymentId());

        try {
            orderService.cancelOrderBySystem(event.getOrderId());
            log.info("Order canceled due to payment failure: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to handle PaymentFailedEvent: orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
        }
    }
}
