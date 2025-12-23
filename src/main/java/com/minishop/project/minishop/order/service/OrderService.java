package com.minishop.project.minishop.order.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderItem;
import com.minishop.project.minishop.order.dto.OrderItemRequest;
import com.minishop.project.minishop.order.repository.OrderRepository;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;

    @Transactional
    public Order createOrder(Long userId, List<OrderItemRequest> itemRequests) {
        validateOrderRequest(itemRequests);

        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest request : itemRequests) {
            // 1. Product 스냅샷 데이터 획득
            Product product = productService.getProductById(request.getProductId());

            // 2. Inventory 예약 (PESSIMISTIC_WRITE lock)
            inventoryService.reserve(request.getProductId(), request.getQuantity());

            // 3. OrderItem 생성 (스냅샷)
            OrderItem orderItem = OrderItem.create(
                    product.getId(),
                    product.getName(),
                    product.getUnitPrice(),
                    request.getQuantity()
            );
            orderItems.add(orderItem);
        }

        // 4. Order 생성 및 저장 (CASCADE로 OrderItems도 저장)
        Order order = Order.create(userId, orderItems);
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 각 OrderItem의 재고 반환
        for (OrderItem item : order.getOrderItems()) {
            inventoryService.release(item.getProductId(), item.getQuantity());
        }

        order.cancel();
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId, Long userId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Transactional
    public Order markAsPaid(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.markAsPaid();
        return orderRepository.save(order);
    }

    @Transactional
    public Order completeOrder(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 결제 완료 시 재고 확정
        for (OrderItem item : order.getOrderItems()) {
            inventoryService.confirm(item.getProductId(), item.getQuantity());
        }

        order.complete();
        return orderRepository.save(order);
    }

    /**
     * 내부용 메서드 - userId 검증 없이 Order 조회
     * Payment 실패 보상, Refund 등에서 사용
     */
    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    @Transactional
    public void expireOrder(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != com.minishop.project.minishop.order.domain.OrderStatus.CREATED) {
            return; // 이미 처리됨
        }

        // 재고 해제
        for (OrderItem item : order.getOrderItems()) {
            inventoryService.release(item.getProductId(), item.getQuantity());
        }

        order.expire();
        orderRepository.save(order);
    }

    @Transactional
    public Order requestRefund(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.requestRefund();
        return orderRepository.save(order);
    }

    @Transactional
    public Order markAsRefunded(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.markAsRefunded();
        return orderRepository.save(order);
    }

    private void validateOrderRequest(List<OrderItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Order must have at least one item");
        }

        for (OrderItemRequest request : itemRequests) {
            if (request.getProductId() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Product ID is required");
            }
            if (request.getQuantity() == null || request.getQuantity() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Quantity must be positive");
            }
        }
    }
}
