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
import com.minishop.project.minishop.refund.domain.RefundItem;
import com.minishop.project.minishop.refund.domain.RefundStatus;
import com.minishop.project.minishop.refund.dto.RefundItemRequest;
import com.minishop.project.minishop.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentService paymentService;
    private final OrderService orderService;
    private final InventoryService inventoryService;

    @Transactional
    public Refund processRefund(Long userId, Long paymentId,
                                List<RefundItemRequest> itemRequests, String reason) {
        // 0. 입력 검증
        validateRefundRequest(paymentId, itemRequests);

        // 1. Payment 조회 및 검증
        Payment payment = paymentService.getPayment(paymentId, userId);

        // 2. Order 조회 및 상태 확인
        Order order = orderService.getOrderById(payment.getOrderId());
        validateOrderForRefund(order);

        // 2.5 Order 상태를 REFUND_REQUESTED로 변경 (첫 환불 요청 시)
        if (order.getStatus() == OrderStatus.PAID) {
            orderService.requestRefund(order.getId());
        }

        // 3. 중복 환불 검증
        validateRefundItems(paymentId, order, itemRequests);

        // 4. RefundItem 생성 (OrderItem에서 스냅샷 복사)
        List<RefundItem> refundItems = createRefundItems(order, itemRequests);

        // 5. Refund 생성 (금액 자동 계산)
        Refund refund = Refund.create(userId, paymentId, order.getId(), refundItems, reason);
        refund = refundRepository.save(refund);

        // 6. 환불 요청 상태로 대기 (관리자 승인 필요)
        // REQUESTED 상태로 저장됨, 관리자 승인 시 처리

        return refund;
    }

    @Transactional
    public Refund approveRefund(Long refundId, String comment) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // 1. 관리자 승인
        refund.approve(comment);

        // 2. 외부 환불 처리 시뮬레이션
        try {
            processExternalRefund(refund);
        } catch (Exception e) {
            refund.markAsFailed();
            return refundRepository.save(refund);
        }

        // 3. 환불 처리 성공
        refund.markAsCompleted();

        // 4. 환불 완료 후 처리 (실패 시 트랜잭션 롤백)
        onRefundCompleted(refund);

        return refundRepository.save(refund);
    }

    @Transactional
    public Refund rejectRefund(Long refundId, String comment) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        refund.reject(comment);
        return refundRepository.save(refund);
    }

    @Transactional(readOnly = true)
    public Refund getRefund(Long refundId, Long userId) {
        return refundRepository.findByIdAndUserId(refundId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Refund getRefundById(Long refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Refund> getRefundsByUser(Long userId) {
        return refundRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Refund> getRefundsByStatus(RefundStatus status) {
        return refundRepository.findByStatus(status);
    }

    private void onRefundCompleted(Refund refund) {
        // Order 상태 업데이트
        updateOrderStatus(refund);

        // RefundItem 기반 정확한 재고 복구
        for (RefundItem item : refund.getRefundItems()) {
            inventoryService.release(item.getProductId(), item.getQuantity());
        }
    }

    private void updateOrderStatus(Refund refund) {
        Long paymentId = refund.getPaymentId();
        Payment payment = paymentService.getPaymentById(paymentId);

        // 완료된 환불 금액 합계 계산
        Long totalRefunded = refundRepository.sumAmountByPaymentIdAndStatus(
                paymentId, RefundStatus.COMPLETED);

        if (totalRefunded.equals(payment.getAmount())) {
            // 전액 환불 완료
            orderService.markAsRefunded(refund.getOrderId());
        }
        // 부분 환불인 경우 Order 상태 유지 (PAID)
    }

    private void validateOrderForRefund(Order order) {
        // PAID 또는 REFUND_REQUESTED 상태에서 환불 요청 가능 (다중 환불 지원)
        if (order.getStatus() != OrderStatus.PAID &&
            order.getStatus() != OrderStatus.REFUND_REQUESTED) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED,
                    "Refund can only be processed for PAID orders");
        }
    }

    private void validateRefundItems(Long paymentId, Order order,
                                     List<RefundItemRequest> requests) {
        // 1. 기존 환불 내역 조회 (APPROVED, COMPLETED만 - 실제 처리된 것)
        List<Refund> existingRefunds = refundRepository.findByPaymentIdAndStatusIn(
                paymentId,
                List.of(RefundStatus.APPROVED, RefundStatus.COMPLETED, RefundStatus.REQUESTED)
        );

        // 2. OrderItem별 이미 환불된/요청된 수량 집계
        Map<Long, Long> refundedQuantityByOrderItem = new HashMap<>();
        for (Refund existingRefund : existingRefunds) {
            for (RefundItem item : existingRefund.getRefundItems()) {
                refundedQuantityByOrderItem.merge(
                        item.getOrderItemId(),
                        item.getQuantity(),
                        Long::sum
                );
            }
        }

        // 3. OrderItem Map 생성 (빠른 조회용)
        Map<Long, OrderItem> orderItemMap = new HashMap<>();
        for (OrderItem item : order.getOrderItems()) {
            orderItemMap.put(item.getId(), item);
        }

        // 4. 요청된 환불 수량 검증
        for (RefundItemRequest request : requests) {
            Long orderItemId = request.getOrderItemId();

            // OrderItem 존재 확인
            OrderItem orderItem = orderItemMap.get(orderItemId);
            if (orderItem == null) {
                throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND,
                        "OrderItem not found: " + orderItemId);
            }

            Long requestedQty = request.getQuantity();
            Long alreadyRefunded = refundedQuantityByOrderItem.getOrDefault(orderItemId, 0L);
            Long originalQty = orderItem.getQuantity();

            if (alreadyRefunded + requestedQty > originalQty) {
                throw new BusinessException(ErrorCode.REFUND_QUANTITY_EXCEEDED,
                        "OrderItem " + orderItemId + ": already refunded/requested " +
                        alreadyRefunded + ", requested " + requestedQty +
                        ", original " + originalQty);
            }
        }
    }

    private List<RefundItem> createRefundItems(Order order, List<RefundItemRequest> requests) {
        // OrderItem Map 생성
        Map<Long, OrderItem> orderItemMap = new HashMap<>();
        for (OrderItem item : order.getOrderItems()) {
            orderItemMap.put(item.getId(), item);
        }

        List<RefundItem> refundItems = new ArrayList<>();
        for (RefundItemRequest request : requests) {
            OrderItem orderItem = orderItemMap.get(request.getOrderItemId());
            // 이미 validateRefundItems에서 검증됨

            RefundItem refundItem = RefundItem.create(
                    orderItem.getId(),
                    orderItem.getProductId(),
                    orderItem.getProductName(),
                    orderItem.getUnitPrice(),
                    request.getQuantity()
            );
            refundItems.add(refundItem);
        }

        return refundItems;
    }

    private void processExternalRefund(Refund refund) {
        // TODO: 외부 환불 처리 로직
        // 현재는 항상 성공으로 처리
    }

    private void validateRefundRequest(Long paymentId, List<RefundItemRequest> itemRequests) {
        if (paymentId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Payment ID is required");
        }
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Refund items are required");
        }
        for (RefundItemRequest item : itemRequests) {
            if (item.getOrderItemId() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Order item ID is required");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Quantity must be positive");
            }
        }
    }
}
