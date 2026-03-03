package com.minishop.project.minishop.outbox.handler;

import com.minishop.project.minishop.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateDeliveryHandler implements RetryTaskHandler {

    private final DeliveryService deliveryService;

    @Override
    public String taskType() {
        return "CREATE_DELIVERY";
    }

    @Override
    public void handle(String payload) {
        Long orderId = PayloadParser.extractLong(payload, "orderId");
        deliveryService.createDeliveryFromOrder(orderId);
        log.info("RetryTask: Delivery created for orderId={}", orderId);
    }
}
