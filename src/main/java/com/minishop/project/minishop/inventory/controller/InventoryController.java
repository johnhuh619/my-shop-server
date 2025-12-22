package com.minishop.project.minishop.inventory.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.inventory.domain.Inventory;
import com.minishop.project.minishop.inventory.dto.AddStockRequest;
import com.minishop.project.minishop.inventory.dto.InventoryResponse;
import com.minishop.project.minishop.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventories")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PatchMapping("/{productId}/add-stock")
    public ApiResponse<InventoryResponse> addStock(
            @PathVariable Long productId,
            @RequestBody AddStockRequest request) {
        Inventory inventory = inventoryService.addStock(productId, request.getQuantity());
        return ApiResponse.success(InventoryResponse.from(inventory));
    }

    @GetMapping("/{productId}")
    public ApiResponse<InventoryResponse> getInventory(@PathVariable Long productId) {
        Inventory inventory = inventoryService.getByProductId(productId);
        return ApiResponse.success(InventoryResponse.from(inventory));
    }
}
