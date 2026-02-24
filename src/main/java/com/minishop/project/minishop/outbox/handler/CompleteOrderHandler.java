package com.minishop.project.minishop.outbox.handler;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteOrderHandler implements RetryTaskHandler {

    private final OrderService orderService;

    @Override
    public String taskType() {
        return "COMPLETE_ORDER";
    }

    @Override
    public void handle(String payload) {
        Long orderId = PayloadParser.extractLong(payload, "orderId");
        try {
            orderService.completeOrder(orderId);
            log.info("RetryTask: Order completed successfully, orderId={}", orderId);
        } catch (BusinessException e) {
            log.warn("RetryTask: Order cannot be completed (business rule), skipping: {}", e.getMessage());
        }
    }
}
