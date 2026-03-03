package com.minishop.project.minishop.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class CreateOrderRequest {
    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank
    private String recipientName;

    @NotBlank
    private String recipientPhone;

    @NotBlank
    private String address;

    private String addressDetail;

    @NotBlank
    private String zipCode;

    public CreateOrderRequest(List<OrderItemRequest> items,
                              String recipientName, String recipientPhone,
                              String address, String addressDetail, String zipCode) {
        this.items = items;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.address = address;
        this.addressDetail = addressDetail;
        this.zipCode = zipCode;
    }
}
