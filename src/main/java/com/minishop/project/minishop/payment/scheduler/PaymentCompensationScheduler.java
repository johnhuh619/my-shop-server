package com.minishop.project.minishop.payment.scheduler;

import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.domain.PaymentStatus;
import com.minishop.project.minishop.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 결제 실패 보상 스케줄러
 *
 * PaymentEventListener에서 재고 release가 부분 실패한 경우를 보상한다.
 * FAILED 상태이면서 5분 이상 지난 결제를 찾아 주문 취소 + 재고 해제를 수행한다.
 * cancelOrderBySystem이 멱등성을 보장하므로 이미 처리된 건은 자동으로 무시된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompensationScheduler {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    private static final long COMPENSATION_DELAY_MINUTES = 5;

    @Scheduled(fixedRate = 300000) // 5분
    public void compensateFailedPayments() {
        Instant cutoff = Instant.now().minus(COMPENSATION_DELAY_MINUTES, ChronoUnit.MINUTES);
        List<Payment> failedPayments = paymentRepository
                .findByStatusAndUpdatedAtBefore(PaymentStatus.FAILED, cutoff);

        for (Payment payment : failedPayments) {
            try {
                orderService.cancelOrderBySystem(payment.getOrderId());
            } catch (Exception e) {
                log.error("Failed to compensate payment: paymentId={}, orderId={}, error={}",
                        payment.getId(), payment.getOrderId(), e.getMessage(), e);
            }
        }
    }
}
