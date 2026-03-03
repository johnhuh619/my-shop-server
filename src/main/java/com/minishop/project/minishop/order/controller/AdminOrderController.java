package com.minishop.project.minishop.order.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.dto.OrderResponse;
import com.minishop.project.minishop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    @PostMapping("/{id}/complete")
    public ApiResponse<OrderResponse> completeOrder(@PathVariable Long id) {
        Order order = orderService.completeOrder(id);
        return ApiResponse.success(OrderResponse.from(order));
    }
}

