package com.minishop.project.minishop.delivery.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeliveryCompletedEvent {
    private final Long deliveryId;
    private final Long orderId;
}
