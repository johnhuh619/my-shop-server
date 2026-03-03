package com.minishop.project.minishop.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ShipDeliveryRequest {

    @NotBlank(message = "Carrier is required")
    private String carrier;

    @NotBlank(message = "Tracking number is required")
    private String trackingNumber;
}
