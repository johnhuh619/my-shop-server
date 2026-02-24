package com.minishop.project.minishop.outbox.handler;

import com.minishop.project.minishop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelOrderHandler implements RetryTaskHandler {

    private final OrderService orderService;

    @Override
    public String taskType() {
        return "CANCEL_ORDER";
    }

    @Override
    public void handle(String payload) {
        Long orderId = PayloadParser.extractLong(payload, "orderId");
        orderService.cancelOrderBySystem(orderId);
        log.info("RetryTask: Order canceled successfully, orderId={}", orderId);
    }
}
