package com.minishop.project.minishop.payment.event;

import com.minishop.project.minishop.delivery.service.DeliveryService;
import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.outbox.service.RetryTaskService;
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
 * - PaymentCompletedEvent: Order 상태 변경 (CREATED → PAID) + 배송 자동 생성
 * - PaymentFailedEvent: 재고 해제 + 주문 취소
 *
 * 실패 시 RetryTaskService에 등록하여 스케줄러가 재시도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderService orderService;
    private final DeliveryService deliveryService;
    private final RetryTaskService retryTaskService;

    /**
     * 결제 완료 이벤트 처리
     * 1. Order 상태를 CREATED → PAID로 변경
     * 2. PREPARING 상태의 Delivery 자동 생성
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
            log.error("Failed to mark order as paid: orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
            retryTaskService.register("MARK_AS_PAID",
                    "{\"orderId\":" + event.getOrderId() + "}");
            return;
        }

        try {
            deliveryService.createDeliveryFromOrder(event.getOrderId());
            log.info("Delivery auto-created: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to auto-create delivery: orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
            retryTaskService.register("CREATE_DELIVERY",
                    "{\"orderId\":" + event.getOrderId() + "}");
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
            retryTaskService.register("CANCEL_ORDER",
                    "{\"orderId\":" + event.getOrderId() + "}");
        }
    }
}
