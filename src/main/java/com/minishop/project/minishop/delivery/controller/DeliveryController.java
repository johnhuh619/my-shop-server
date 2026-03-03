package com.minishop.project.minishop.delivery.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.common.util.AuthenticationContext;
import com.minishop.project.minishop.delivery.domain.Delivery;
import com.minishop.project.minishop.delivery.dto.DeliveryResponse;
import com.minishop.project.minishop.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    public ApiResponse<List<DeliveryResponse>> getMyDeliveries() {
        Long userId = AuthenticationContext.getCurrentUserId();
        List<Delivery> deliveries = deliveryService.getDeliveriesByUserId(userId);
        List<DeliveryResponse> responses = deliveries.stream()
                .map(DeliveryResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{id}")
    public ApiResponse<DeliveryResponse> getDelivery(@PathVariable Long id) {
        Long userId = AuthenticationContext.getCurrentUserId();
        Delivery delivery = deliveryService.getDeliveryByIdAndUserId(id, userId);
        return ApiResponse.success(DeliveryResponse.from(delivery));
    }

    @GetMapping("/order/{orderId}")
    public ApiResponse<DeliveryResponse> getDeliveryByOrderId(@PathVariable Long orderId) {
        Long userId = AuthenticationContext.getCurrentUserId();
        Delivery delivery = deliveryService.getDeliveryByOrderIdAndUserId(orderId, userId);
        return ApiResponse.success(DeliveryResponse.from(delivery));
    }
}
