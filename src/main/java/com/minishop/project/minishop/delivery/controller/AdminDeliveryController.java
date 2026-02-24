package com.minishop.project.minishop.delivery.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.delivery.domain.Delivery;
import com.minishop.project.minishop.delivery.domain.DeliveryStatus;
import com.minishop.project.minishop.delivery.dto.CreateDeliveryRequest;
import com.minishop.project.minishop.delivery.dto.DeliveryResponse;
import com.minishop.project.minishop.delivery.dto.ShipDeliveryRequest;
import com.minishop.project.minishop.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryController {

    private final DeliveryService deliveryService;

    @PostMapping
    public ApiResponse<DeliveryResponse> createDelivery(@Valid @RequestBody CreateDeliveryRequest request) {
        Delivery delivery = deliveryService.createDelivery(request);
        return ApiResponse.success(DeliveryResponse.from(delivery));
    }

    @PostMapping("/{id}/ship")
    public ApiResponse<DeliveryResponse> shipDelivery(
            @PathVariable Long id, @Valid @RequestBody ShipDeliveryRequest request) {
        Delivery delivery = deliveryService.shipDelivery(id, request);
        return ApiResponse.success(DeliveryResponse.from(delivery));
    }

    @PostMapping("/{id}/in-transit")
    public ApiResponse<DeliveryResponse> markInTransit(@PathVariable Long id) {
        Delivery delivery = deliveryService.markInTransit(id);
        return ApiResponse.success(DeliveryResponse.from(delivery));
    }

    @PostMapping("/{id}/deliver")
    public ApiResponse<DeliveryResponse> markDelivered(@PathVariable Long id) {
        Delivery delivery = deliveryService.markDelivered(id);
        return ApiResponse.success(DeliveryResponse.from(delivery));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<DeliveryResponse> cancelDelivery(@PathVariable Long id) {
        Delivery delivery = deliveryService.cancelDelivery(id);
        return ApiResponse.success(DeliveryResponse.from(delivery));
    }

    @GetMapping
    public ApiResponse<List<DeliveryResponse>> getDeliveries(
            @RequestParam(required = false) DeliveryStatus status) {
        List<Delivery> deliveries;
        if (status != null) {
            deliveries = deliveryService.getDeliveriesByStatus(status);
        } else {
            deliveries = deliveryService.getAllDeliveries();
        }
        List<DeliveryResponse> responses = deliveries.stream()
                .map(DeliveryResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{id}")
    public ApiResponse<DeliveryResponse> getDelivery(@PathVariable Long id) {
        Delivery delivery = deliveryService.getDeliveryById(id);
        return ApiResponse.success(DeliveryResponse.from(delivery));
    }
}
