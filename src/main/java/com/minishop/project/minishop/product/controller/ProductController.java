package com.minishop.project.minishop.product.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.common.response.PageResponse;
import com.minishop.project.minishop.product.dto.ProductResponse;
import com.minishop.project.minishop.product.dto.ProductWithStock;
import com.minishop.project.minishop.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable Long id) {
        ProductWithStock pws = productService.getActiveProductWithStock(id);
        return ApiResponse.success(ProductResponse.from(pws.product(), pws.quantityAvailable()));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> getActiveProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by("createdAt").descending());
        Page<ProductWithStock> result = productService.getActiveProductsWithStock(keyword, pageable);
        PageResponse<ProductResponse> response = PageResponse.of(result,
                pws -> ProductResponse.from(pws.product(), pws.quantityAvailable()));
        return ApiResponse.success(response);
    }
}
