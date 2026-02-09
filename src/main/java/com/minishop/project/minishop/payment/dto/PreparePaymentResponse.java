package com.minishop.project.minishop.payment.dto;

import com.minishop.project.minishop.payment.domain.Payment;
import lombok.Getter;

@Getter
public class PreparePaymentResponse {
    private final Long paymentId;
    private final String tossOrderId;
    private final Long amount;
    private final String orderName;

    private PreparePaymentResponse(Long paymentId, String tossOrderId, Long amount, String orderName) {
        this.paymentId = paymentId;
        this.tossOrderId = tossOrderId;
        this.amount = amount;
        this.orderName = orderName;
    }

    public static PreparePaymentResponse from(Payment payment, String orderName) {
        return new PreparePaymentResponse(
                payment.getId(),
                payment.getTossOrderId(),
                payment.getAmount(),
                orderName
        );
    }
}
