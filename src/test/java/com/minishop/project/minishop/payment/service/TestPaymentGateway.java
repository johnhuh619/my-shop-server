package com.minishop.project.minishop.payment.service;

import com.minishop.project.minishop.payment.dto.TossConfirmResponse;
import com.minishop.project.minishop.payment.gateway.PaymentGateway;

/**
 * 테스트용 PaymentGateway 구현체
 * - 결제 승인 성공/실패 시나리오를 제어할 수 있음
 */
public class TestPaymentGateway implements PaymentGateway {

    private boolean shouldFail = false;
    private String failureMessage = "Test PG Failure";

    @Override
    public TossConfirmResponse confirmPayment(String paymentKey, String tossOrderId, Long amount) {
        if (shouldFail) {
            throw new RuntimeException(failureMessage);
        }
        return new TossConfirmResponse();
    }

    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public void reset() {
        this.shouldFail = false;
        this.failureMessage = "Test PG Failure";
    }
}
