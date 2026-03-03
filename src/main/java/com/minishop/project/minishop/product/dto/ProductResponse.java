package com.minishop.project.minishop.product.dto;

import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.domain.ProductStatus;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
public class ProductResponse {
    private final Long id;
    private final String name;
    private final String description;
    private final Long unitPrice;
    private final ProductStatus status;
    private final Long quantityAvailable;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ProductResponse(Long id, String name, String description, Long unitPrice,
                           ProductStatus status, Long quantityAvailable,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.unitPrice = unitPrice;
        this.status = status;
        this.quantityAvailable = quantityAvailable;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ProductResponse from(Product product) {
        return from(product, null);
    }

    public static ProductResponse from(Product product, Long quantityAvailable) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getUnitPrice(),
                product.getStatus(),
                quantityAvailable,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
