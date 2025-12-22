package com.minishop.project.minishop.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AddStockRequest {
    private Long quantity;

    public AddStockRequest(Long quantity) {
        this.quantity = quantity;
    }
}
