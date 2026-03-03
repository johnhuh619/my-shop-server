package com.minishop.project.minishop.outbox.handler;

import com.minishop.project.minishop.delivery.service.DeliveryService;
import com.minishop.project.minishop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarkAsPaidHandler implements RetryTaskHandler {

    private final OrderService orderService;
    private final DeliveryService deliveryService;

    @Override
    public String taskType() {
        return "MARK_AS_PAID";
    }

    @Override
    public void handle(String payload) {
        Long orderId = PayloadParser.extractLong(payload, "orderId");
        orderService.markAsPaid(orderId);
        log.info("RetryTask: Order marked as paid, orderId={}", orderId);

        deliveryService.createDeliveryFromOrder(orderId);
        log.info("RetryTask: Delivery auto-created after markAsPaid retry, orderId={}", orderId);
    }
}
