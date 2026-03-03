package com.minishop.project.minishop.product.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateProductRequest {
    private String name;
    private String description;
    private Long unitPrice;

    public UpdateProductRequest(String name, String description, Long unitPrice) {
        this.name = name;
        this.description = description;
        this.unitPrice = unitPrice;
    }
}
