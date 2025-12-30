package com.minishop.project.minishop.payment.event;

import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderItem;
import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.gateway.PaymentGateway;
import com.minishop.project.minishop.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

/**
 * Payment 이벤트 리스너
 *
 * 트랜잭션 커밋 후 비동기로 실행되어 PG 호출, Order 상태 변경 및 재고 관리 수행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 결제 생성 이벤트 처리
     * - 외부 PG 호출을 비동기로 처리
     * - 성공 시 PaymentCompletedEvent 발행
     * - 실패 시 PaymentFailedEvent 발행
     */
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        log.info("[{}] Processing PaymentCreatedEvent: paymentId={}, orderId={}",
                Thread.currentThread().getName(), event.getPaymentId(), event.getOrderId());

        try {
            // 1. Payment 재조회
            Payment payment = paymentRepository.findById(event.getPaymentId())
                    .orElseThrow(() -> new RuntimeException("Payment not found: " + event.getPaymentId()));

            // 2. 외부 PG 호출 (3초 소요)
            paymentGateway.processPayment(payment);
            log.info("PG payment processed successfully: paymentId={}", event.getPaymentId());

            // 3. 결제 성공 처리
            payment.markAsCompleted();
            paymentRepository.save(payment);

            // 4. 결제 완료 이벤트 발행
            eventPublisher.publishEvent(PaymentCompletedEvent.from(payment));
            log.info("PaymentCompletedEvent published: paymentId={}", event.getPaymentId());

        } catch (Exception e) {
            log.error("PG payment failed: paymentId={}, error={}",
                    event.getPaymentId(), e.getMessage(), e);

            try {
                // 1. Payment 재조회 (예외 발생 시 트랜잭션 롤백 대비)
                Payment payment = paymentRepository.findById(event.getPaymentId())
                        .orElseThrow(() -> new RuntimeException("Payment not found: " + event.getPaymentId()));

                // 2. 결제 실패 처리
                payment.markAsFailed();
                paymentRepository.save(payment);

                // 3. OrderItems 조회 (재고 해제용)
                Order order = orderService.getOrderById(payment.getOrderId());
                List<OrderItem> orderItems = order.getOrderItems();

                // 4. 결제 실패 이벤트 발행
                eventPublisher.publishEvent(PaymentFailedEvent.from(payment, orderItems));
                log.info("PaymentFailedEvent published: paymentId={}", event.getPaymentId());

            } catch (Exception failureHandlingError) {
                log.error("Failed to handle payment failure: paymentId={}, error={}",
                        event.getPaymentId(), failureHandlingError.getMessage(), failureHandlingError);
            }
        }
    }

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
            // Spring Event의 한계: 재시도 로직 없음, 로깅으로만 추적
        }
    }

    /**
     * 결제 실패 이벤트 처리
     * - 예약된 재고 해제
     */
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("[{}] Processing PaymentFailedEvent: orderId={}, paymentId={}",
                Thread.currentThread().getName(), event.getOrderId(), event.getPaymentId());

        try {
            for (PaymentFailedEvent.OrderItemSnapshot item : event.getOrderItems()) {
                inventoryService.release(item.productId(), item.quantity());
                log.info("Inventory released: productId={}, quantity={}",
                        item.productId(), item.quantity());
            }
        } catch (Exception e) {
            log.error("Failed to handle PaymentFailedEvent: orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
            // Spring Event의 한계: 재시도 로직 없음, 로깅으로만 추적
        }
    }
}
