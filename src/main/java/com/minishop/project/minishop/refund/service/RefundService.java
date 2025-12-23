package com.minishop.project.minishop.refund.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderItem;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.service.PaymentService;
import com.minishop.project.minishop.refund.domain.Refund;
import com.minishop.project.minishop.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentService paymentService;
    private final OrderService orderService;
    private final InventoryService inventoryService;

    @Transactional
    public Refund processRefund(Long userId, Long paymentId, Long amount, String reason) {
        // 1. Payment 조회 및 검증
        Payment payment = paymentService.getPayment(paymentId, userId);

        // 2. Order 조회 및 상태 확인
        Order order = orderService.getOrderById(payment.getOrderId());
        validateOrderForRefund(order);

        // 3. 환불 금액 검증 (null이면 전액 환불)
        Long refundAmount = (amount != null) ? amount : payment.getAmount();
        validateRefundAmount(refundAmount, payment.getAmount());

        // 4. Refund 생성
        Refund refund = Refund.create(
                userId, paymentId, order.getId(), refundAmount, reason
        );
        refund = refundRepository.save(refund);

        // 5. Order 상태 변경 (PAID → REFUND_REQUESTED)
        orderService.requestRefund(order.getId());

        // 6. 외부 환불 처리 시뮬레이션
        try {
            processExternalRefund(refund);
            refund.markAsCompleted();

            // 7. 환불 성공 후 처리 (추후 이벤트로 전환 가능)
            onRefundCompleted(refund, order.getId());

        } catch (Exception e) {
            refund.markAsFailed();
        }

        return refundRepository.save(refund);
    }

    @Transactional(readOnly = true)
    public Refund getRefund(Long refundId, Long userId) {
        return refundRepository.findByIdAndUserId(refundId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Refund> getRefundsByUser(Long userId) {
        return refundRepository.findByUserId(userId);
    }

    // 추후 이벤트 발행으로 전환 가능한 메서드
    private void onRefundCompleted(Refund refund, Long orderId) {
        // Order 상태 변경 (REFUND_REQUESTED → REFUNDED)
        orderService.markAsRefunded(orderId);

        // 재고 복구 (정책: 환불 시 재고 복구)
        Order order = orderService.getOrderById(orderId);
        for (OrderItem item : order.getOrderItems()) {
            inventoryService.release(item.getProductId(), item.getQuantity());
        }
    }

    private void validateOrderForRefund(Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED,
                    "Refund can only be processed for PAID orders");
        }
    }

    private void validateRefundAmount(Long refundAmount, Long paymentAmount) {
        if (refundAmount == null || refundAmount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_AMOUNT,
                    "Refund amount must be positive");
        }
        if (refundAmount > paymentAmount) {
            throw new BusinessException(ErrorCode.REFUND_AMOUNT_EXCEEDED,
                    "Refund amount cannot exceed payment amount");
        }
    }

    private void processExternalRefund(Refund refund) {
        // TODO: 외부 환불 처리 로직
        // 현재는 항상 성공으로 처리
    }
}
