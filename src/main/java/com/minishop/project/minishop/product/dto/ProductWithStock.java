package com.minishop.project.minishop.product.dto;

import com.minishop.project.minishop.product.domain.Product;

public record ProductWithStock(Product product, Long quantityAvailable) {
}
