package com.minishop.project.minishop.product.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.dto.CreateProductRequest;
import com.minishop.project.minishop.product.dto.ProductResponse;
import com.minishop.project.minishop.product.dto.UpdateProductRequest;
import com.minishop.project.minishop.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    @PostMapping
    public ApiResponse<ProductResponse> createProduct(@RequestBody CreateProductRequest request) {
        Product product = productService.createProduct(
                request.getName(),
                request.getDescription(),
                request.getUnitPrice()
        );
        return ApiResponse.success(ProductResponse.from(product));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProductResponse> updateProduct(
            @PathVariable Long id,
            @RequestBody UpdateProductRequest request) {
        Product product = productService.updateProduct(
                id,
                request.getName(),
                request.getDescription(),
                request.getUnitPrice()
        );
        return ApiResponse.success(ProductResponse.from(product));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<ProductResponse> deactivateProduct(@PathVariable Long id) {
        Product product = productService.deactivateProduct(id);
        return ApiResponse.success(ProductResponse.from(product));
    }
}
