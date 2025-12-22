package com.minishop.project.minishop.product.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateProductRequest {
    private String name;
    private String description;
    private Long unitPrice;

    public CreateProductRequest(String name, String description, Long unitPrice) {
        this.name = name;
        this.description = description;
        this.unitPrice = unitPrice;
    }
}
