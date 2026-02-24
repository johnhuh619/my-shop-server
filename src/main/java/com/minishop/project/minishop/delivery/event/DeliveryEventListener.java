package com.minishop.project.minishop.delivery.event;

import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.outbox.service.RetryTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventListener {

    private final OrderService orderService;
    private final RetryTaskService retryTaskService;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handleDeliveryCompleted(DeliveryCompletedEvent event) {
        log.info("[{}] Processing DeliveryCompletedEvent: deliveryId={}, orderId={}",
                Thread.currentThread().getName(), event.getDeliveryId(), event.getOrderId());

        try {
            orderService.completeOrder(event.getOrderId());
            log.info("Order status updated to COMPLETED: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to handle DeliveryCompletedEvent: orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
            retryTaskService.register("COMPLETE_ORDER",
                    "{\"orderId\":" + event.getOrderId() + "}");
        }
    }
}
