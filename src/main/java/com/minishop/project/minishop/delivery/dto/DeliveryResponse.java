package com.minishop.project.minishop.delivery.dto;

import com.minishop.project.minishop.delivery.domain.Delivery;
import com.minishop.project.minishop.delivery.domain.DeliveryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class DeliveryResponse {
    private Long id;
    private Long orderId;
    private Long userId;
    private DeliveryStatus status;
    private String recipientName;
    private String recipientPhone;
    private String address;
    private String addressDetail;
    private String zipCode;
    private String carrier;
    private String trackingNumber;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant shippedAt;
    private Instant deliveredAt;

    public static DeliveryResponse from(Delivery delivery) {
        return DeliveryResponse.builder()
                .id(delivery.getId())
                .orderId(delivery.getOrderId())
                .userId(delivery.getUserId())
                .status(delivery.getStatus())
                .recipientName(delivery.getRecipientName())
                .recipientPhone(delivery.getRecipientPhone())
                .address(delivery.getAddress())
                .addressDetail(delivery.getAddressDetail())
                .zipCode(delivery.getZipCode())
                .carrier(delivery.getCarrier())
                .trackingNumber(delivery.getTrackingNumber())
                .createdAt(delivery.getCreatedAt())
                .updatedAt(delivery.getUpdatedAt())
                .shippedAt(delivery.getShippedAt())
                .deliveredAt(delivery.getDeliveredAt())
                .build();
    }
}
