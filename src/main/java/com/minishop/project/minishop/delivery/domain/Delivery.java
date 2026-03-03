package com.minishop.project.minishop.delivery.domain;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String recipientPhone;

    @Column(nullable = false)
    private String address;

    private String addressDetail;

    @Column(nullable = false)
    private String zipCode;

    private String carrier;

    private String trackingNumber;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant shippedAt;

    private Instant deliveredAt;

    @Builder
    private Delivery(Long orderId, Long userId, DeliveryStatus status,
                     String recipientName, String recipientPhone,
                     String address, String addressDetail, String zipCode,
                     Instant createdAt, Instant updatedAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.status = status;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.address = address;
        this.addressDetail = addressDetail;
        this.zipCode = zipCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Delivery create(Long orderId, Long userId,
                                   String recipientName, String recipientPhone,
                                   String address, String addressDetail, String zipCode) {
        Instant now = Instant.now();
        return Delivery.builder()
                .orderId(orderId)
                .userId(userId)
                .status(DeliveryStatus.PREPARING)
                .recipientName(recipientName)
                .recipientPhone(recipientPhone)
                .address(address)
                .addressDetail(addressDetail)
                .zipCode(zipCode)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void ship(String carrier, String trackingNumber) {
        validateStatusTransition(DeliveryStatus.SHIPPED);
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.status = DeliveryStatus.SHIPPED;
        this.shippedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markInTransit() {
        validateStatusTransition(DeliveryStatus.IN_TRANSIT);
        this.status = DeliveryStatus.IN_TRANSIT;
        this.updatedAt = Instant.now();
    }

    public void markDelivered() {
        validateStatusTransition(DeliveryStatus.DELIVERED);
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (this.status == DeliveryStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.INVALID_DELIVERY_STATUS,
                    "Cannot cancel a delivered delivery");
        }
        if (this.status == DeliveryStatus.CANCELED) {
            return;
        }
        this.status = DeliveryStatus.CANCELED;
        this.updatedAt = Instant.now();
    }

    private void validateStatusTransition(DeliveryStatus target) {
        boolean valid = switch (target) {
            case SHIPPED -> this.status == DeliveryStatus.PREPARING;
            case IN_TRANSIT -> this.status == DeliveryStatus.SHIPPED;
            case DELIVERED -> this.status == DeliveryStatus.SHIPPED || this.status == DeliveryStatus.IN_TRANSIT;
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_DELIVERY_STATUS,
                    "Cannot transition from " + this.status + " to " + target);
        }
    }
}
