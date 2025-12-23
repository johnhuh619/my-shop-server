package com.minishop.project.minishop.payment.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    @Transactional
    public Payment processPayment(Long userId, Long orderId, String idempotencyKey) {
        // 1. 멱등성 체크 - 동일 키로 결제 존재하면 기존 결제 반환
        Optional<Payment> existingPayment =
                paymentRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment existing = existingPayment.get();
            // 같은 키로 다른 주문 결제 시도 시 에러
            if (!existing.getOrderId().equals(orderId)) {
                throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT);
            }
            return existing;
        }

        // 2. Order 조회 및 검증 (소유권 + 상태)
        Order order = orderService.getOrder(orderId, userId);
        validateOrderForPayment(order);

        // 3. Payment 생성 (스냅샷)
        Payment payment = Payment.create(
                userId, orderId, idempotencyKey, order.getTotalAmount()
        );
        payment = paymentRepository.save(payment);

        // 4. 결제 처리 (외부 PG 연동 시뮬레이션)
        try {
            processExternalPayment(payment);
            payment.markAsCompleted();

            // 5. 결제 성공 후 처리 (추후 이벤트로 전환 가능)
            onPaymentCompleted(payment);

        } catch (Exception e) {
            payment.markAsFailed();
        }

        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId, Long userId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByUser(Long userId) {
        return paymentRepository.findByUserId(userId);
    }

    // 추후 이벤트 발행으로 전환 가능한 메서드
    // DOMAIN_RULES: Payment는 Inventory를 직접 조작하지 않음
    // Inventory confirm은 OrderService.completeOrder()에서 처리
    private void onPaymentCompleted(Payment payment) {
        // Order 상태만 변경 (CREATED → PAID)
        orderService.markAsPaid(payment.getOrderId());
    }

    private void validateOrderForPayment(Order order) {
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Order must be in CREATED status to process payment");
        }
    }

    private void processExternalPayment(Payment payment) {
        // TODO: 외부 PG 연동 로직
        // 현재는 항상 성공으로 처리
    }
}
