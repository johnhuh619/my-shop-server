package com.minishop.project.minishop.payment.service;

import com.minishop.project.minishop.payment.dto.TossCancelResponse;
import com.minishop.project.minishop.payment.dto.TossConfirmResponse;
import com.minishop.project.minishop.payment.gateway.PaymentGateway;

/**
 * 테스트용 PaymentGateway 구현체
 * - 결제 승인/취소 성공/실패 시나리오를 제어할 수 있음
 */
public class TestPaymentGateway implements PaymentGateway {

    private boolean shouldFail = false;
    private String failureMessage = "Test PG Failure";

    private boolean shouldCancelFail = false;
    private String cancelFailureMessage = "Test PG Cancel Failure";

    @Override
    public TossConfirmResponse confirmPayment(String paymentKey, String tossOrderId, Long amount) {
        if (shouldFail) {
            throw new RuntimeException(failureMessage);
        }
        return new TossConfirmResponse();
    }

    @Override
    public TossCancelResponse cancelPayment(String paymentKey, String cancelReason, Long cancelAmount) {
        if (shouldCancelFail) {
            throw new RuntimeException(cancelFailureMessage);
        }
        return new TossCancelResponse();
    }

    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public void setShouldCancelFail(boolean shouldCancelFail) {
        this.shouldCancelFail = shouldCancelFail;
    }

    public void setCancelFailureMessage(String cancelFailureMessage) {
        this.cancelFailureMessage = cancelFailureMessage;
    }

    public void reset() {
        this.shouldFail = false;
        this.failureMessage = "Test PG Failure";
        this.shouldCancelFail = false;
        this.cancelFailureMessage = "Test PG Cancel Failure";
    }
}
