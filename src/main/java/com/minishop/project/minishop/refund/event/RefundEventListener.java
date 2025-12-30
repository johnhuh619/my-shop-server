package com.minishop.project.minishop.refund.event;

import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

/**
 * Refund 이벤트 리스너
 *
 * 트랜잭션 커밋 후 비동기로 실행되어 Order 상태 변경 및 재고 복구 수행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundEventListener {

    private final OrderService orderService;
    private final InventoryService inventoryService;

    /**
     * 환불 완료 이벤트 처리
     * - 전액 환불 시 Order 상태를 REFUND_REQUESTED → REFUNDED로 변경
     * - RefundItem 기반 재고 복구
     */
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handleRefundCompleted(RefundCompletedEvent event) {
        log.info("[{}] Processing RefundCompletedEvent: orderId={}, refundId={}, totalRefunded={}, paymentAmount={}",
                Thread.currentThread().getName(), event.getOrderId(), event.getRefundId(),
                event.getTotalRefundedAmount(), event.getPaymentAmount());

        try {
            // 전액 환불 시 Order 상태 변경
            if (event.getTotalRefundedAmount().equals(event.getPaymentAmount())) {
                orderService.markAsRefunded(event.getOrderId());
                log.info("Order status updated to REFUNDED (full refund): orderId={}", event.getOrderId());
            } else {
                log.info("Partial refund: orderId={}, refundedAmount={}/{} (Order status remains)",
                        event.getOrderId(), event.getTotalRefundedAmount(), event.getPaymentAmount());
            }

            // RefundItem 기반 재고 복구
            for (RefundCompletedEvent.RefundItemSnapshot item : event.getRefundItems()) {
                inventoryService.release(item.productId(), item.quantity());
                log.info("Inventory restored: productId={}, quantity={}",
                        item.productId(), item.quantity());
            }
        } catch (Exception e) {
            log.error("Failed to handle RefundCompletedEvent: orderId={}, refundId={}, error={}",
                    event.getOrderId(), event.getRefundId(), e.getMessage(), e);
            // Spring Event의 한계: 재시도 로직 없음, 로깅으로만 추적
        }
    }
}
