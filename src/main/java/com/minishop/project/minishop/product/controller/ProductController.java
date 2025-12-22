package com.minishop.project.minishop.product.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.dto.CreateProductRequest;
import com.minishop.project.minishop.product.dto.ProductResponse;
import com.minishop.project.minishop.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

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

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        return ApiResponse.success(ProductResponse.from(product));
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> getActiveProducts() {
        List<Product> products = productService.getActiveProducts();
        List<ProductResponse> responses = products.stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }
}
