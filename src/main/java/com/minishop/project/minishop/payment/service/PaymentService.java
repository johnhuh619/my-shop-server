package com.minishop.project.minishop.payment.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderItem;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.gateway.PaymentGateway;
import com.minishop.project.minishop.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final PaymentGateway paymentGateway;

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

        try {
            // 3. Payment 생성 (스냅샷)
            Payment payment = Payment.create(
                    userId, orderId, idempotencyKey, order.getTotalAmount()
            );
            payment = paymentRepository.save(payment);
            paymentRepository.flush(); // Force immediate DB write to detect UNIQUE constraint violation

            // 4. 결제 처리 (외부 PG 연동)
            try {
                paymentGateway.processPayment(payment);
                payment.markAsCompleted();

                // 5. 결제 성공 후 처리 (추후 이벤트로 전환 가능)
                onPaymentCompleted(payment);

            } catch (Exception e) {
                payment.markAsFailed();
                // 결제 실패 시 재고 보상 (추후 이벤트로 전환 가능)
                onPaymentFailed(payment, orderId);
            }

            return paymentRepository.save(payment);

        } catch (DataIntegrityViolationException e) {
            // 동시성 이슈: 다른 트랜잭션에서 이미 같은 키로 Payment 생성
            // UNIQUE 제약 조건 위반 시 재조회하여 기존 Payment 반환
            Optional<Payment> retryPayment =
                    paymentRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (retryPayment.isPresent()) {
                Payment existing = retryPayment.get();
                // 같은 키로 다른 주문 결제 시도 시 에러
                if (!existing.getOrderId().equals(orderId)) {
                    throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT);
                }
                return existing;
            }
            // 예상치 못한 제약 조건 위반
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId, Long userId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Payment getPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId)
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

    // 추후 이벤트 발행으로 전환 가능한 메서드
    // 결제 실패 시 예약된 재고 해제
    private void onPaymentFailed(Payment payment, Long orderId) {
        Order order = orderService.getOrderById(orderId);
        for (OrderItem item : order.getOrderItems()) {
            inventoryService.release(item.getProductId(), item.getQuantity());
        }
    }

    private void validateOrderForPayment(Order order) {
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Order must be in CREATED status to process payment");
        }
    }
}
