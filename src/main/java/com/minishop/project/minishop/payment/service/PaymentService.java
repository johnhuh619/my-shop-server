package com.minishop.project.minishop.payment.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderItem;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.repository.OrderRepository;
import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.dto.TossCancelResponse;
import com.minishop.project.minishop.payment.gateway.PaymentGateway;
import com.minishop.project.minishop.payment.repository.PaymentRepository;
import com.minishop.project.minishop.payment.service.PaymentConfirmHandler.ConfirmPreparation;
import com.minishop.project.minishop.payment.service.PaymentConfirmHandler.ConfirmPreparationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentConfirmHandler confirmHandler;

    @Transactional
    public Payment preparePayment(Long userId, Long orderId, String idempotencyKey) {
        // 1. 멱등성 체크 - 동일 키로 결제 존재하면 기존 결제 반환
        Optional<Payment> existingPayment =
                paymentRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment existing = existingPayment.get();
            if (!existing.getOrderId().equals(orderId)) {
                throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT);
            }
            return existing;
        }

        // 2. Order 조회 및 검증 (소유권 + 상태)
        Order order = orderService.getOrder(orderId, userId);
        validateOrderForPayment(order);

        try {
            // 3. Payment 생성 (REQUESTED 상태, tossOrderId 자동 생성)
            Payment payment = Payment.create(
                    userId, orderId, idempotencyKey, order.getTotalAmount()
            );
            payment = paymentRepository.save(payment);
            paymentRepository.flush();

            return payment;

        } catch (DataIntegrityViolationException e) {
            Optional<Payment> retryPayment =
                    paymentRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (retryPayment.isPresent()) {
                Payment existing = retryPayment.get();
                if (!existing.getOrderId().equals(orderId)) {
                    throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT);
                }
                return existing;
            }
            throw e;
        }
    }

    public Payment confirmPayment(Long userId, String paymentKey, String tossOrderId, Long amount) {
        ConfirmPreparation preparation = confirmHandler.prepareConfirm(userId, paymentKey, tossOrderId, amount);

        if (preparation.type() == ConfirmPreparationType.COMPLETED) {
            return preparation.payment();
        }
        if (preparation.type() == ConfirmPreparationType.IN_PROGRESS) {
            return confirmHandler.waitForCompletion(userId, tossOrderId);
        }

        try {
            // External call must be outside DB transaction to minimize lock holding time.
            paymentGateway.confirmPayment(paymentKey, tossOrderId, amount);
        } catch (Exception e) {
            log.error("PG confirm failed: tossOrderId={}, error={}", tossOrderId, e.getMessage());
            confirmHandler.finalizeConfirmFailure(userId, tossOrderId);
            throw new BusinessException(ErrorCode.PG_CONFIRM_FAILED, e.getMessage());
        }

        return confirmHandler.finalizeConfirmSuccess(userId, tossOrderId);
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

    public TossCancelResponse cancelPayment(Long paymentId, String cancelReason, Long cancelAmount) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        return paymentGateway.cancelPayment(payment.getPaymentKey(), cancelReason, cancelAmount);
    }

    public String buildOrderName(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId).orElse(null);
        if (order == null || order.getOrderItems().isEmpty()) {
            return "주문";
        }
        List<OrderItem> items = order.getOrderItems();
        String firstName = items.get(0).getProductName();
        if (items.size() == 1) {
            return firstName;
        }
        return firstName + " 외 " + (items.size() - 1) + "건";
    }

    private void validateOrderForPayment(Order order) {
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Order must be in CREATED status to process payment");
        }
    }
}
