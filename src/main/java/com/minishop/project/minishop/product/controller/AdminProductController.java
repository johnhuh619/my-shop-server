package com.minishop.project.minishop.product.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.common.response.PageResponse;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.dto.CreateProductRequest;
import com.minishop.project.minishop.product.dto.ProductResponse;
import com.minishop.project.minishop.product.dto.ProductWithStock;
import com.minishop.project.minishop.product.dto.UpdateProductRequest;
import com.minishop.project.minishop.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private enum ProductStatusFilter {
        ACTIVE,
        INACTIVE,
        ALL
    }

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

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ACTIVE") ProductStatusFilter status,
            @RequestParam(required = false) String keyword
    ) {
        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by("createdAt").descending());
        Page<ProductWithStock> result = switch (status) {
            case ACTIVE -> productService.getActiveProductsWithStock(keyword, pageable);
            case INACTIVE -> productService.getInactiveProductsWithStock(keyword, pageable);
            case ALL -> productService.getProductsWithStock(keyword, pageable);
        };
        PageResponse<ProductResponse> response = PageResponse.of(result,
                pws -> ProductResponse.from(pws.product(), pws.quantityAvailable()));
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<ProductResponse> deactivateProduct(@PathVariable Long id) {
        Product product = productService.deactivateProduct(id);
        return ApiResponse.success(ProductResponse.from(product));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<ProductResponse> activateProduct(@PathVariable Long id) {
        Product product = productService.activateProduct(id);
        return ApiResponse.success(ProductResponse.from(product));
    }
}
