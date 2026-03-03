package com.minishop.project.minishop.order.service;

import com.minishop.project.minishop.delivery.domain.DeliveryStatus;
import com.minishop.project.minishop.delivery.service.DeliveryService;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.dto.OrderResponse;
import com.minishop.project.minishop.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderService orderService;
    private final RefundRepository refundRepository;
    private final DeliveryService deliveryService;

    public OrderResponse getOrderDetail(Long orderId, Long userId) {
        Order order = orderService.getOrder(orderId, userId);
        Set<Long> refundedItemIds = refundRepository.findCompletedRefundItemIdsByOrderId(orderId);
        return OrderResponse.from(order, refundedItemIds);
    }

    public List<OrderResponse> getOrderList(Long userId) {
        List<Order> orders = orderService.getOrdersByUserWithItems(userId);
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();

        // 3개 배치 조회 (각 도메인 서비스/레포지토리 위임)
        Map<Long, Set<Long>> refundMap = refundRepository
                .findCompletedRefundItemIdsByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],
                        Collectors.mapping(row -> (Long) row[1], Collectors.toSet())));

        Map<Long, DeliveryStatus> deliveryMap = deliveryService.getStatusMapByOrderIds(orderIds);

        return orders.stream()
                .map(order -> OrderResponse.from(
                        order,
                        refundMap.getOrDefault(order.getId(), Set.of()),
                        deliveryMap.get(order.getId())
                ))
                .toList();
    }
}
