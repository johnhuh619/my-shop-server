package com.minishop.project.minishop.order.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.common.util.AuthenticationContext;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.dto.CreateOrderRequest;
import com.minishop.project.minishop.order.dto.OrderResponse;
import com.minishop.project.minishop.order.service.OrderQueryService;
import com.minishop.project.minishop.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Long userId = AuthenticationContext.getCurrentUserId();
        Order order = orderService.createOrder(userId, request.getItems(),
                request.getRecipientName(), request.getRecipientPhone(),
                request.getAddress(), request.getAddressDetail(), request.getZipCode());
        return ApiResponse.success(OrderResponse.from(order));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> getMyOrders() {
        Long userId = AuthenticationContext.getCurrentUserId();
        return ApiResponse.success(orderQueryService.getOrderList(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long id) {
        Long userId = AuthenticationContext.getCurrentUserId();
        return ApiResponse.success(orderQueryService.getOrderDetail(id, userId));
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long id) {
        Long userId = AuthenticationContext.getCurrentUserId();
        Order order = orderService.cancelOrder(id, userId);
        return ApiResponse.success(OrderResponse.from(order));
    }
}
