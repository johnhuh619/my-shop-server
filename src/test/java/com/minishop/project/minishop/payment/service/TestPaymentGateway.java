package com.minishop.project.minishop.payment.service;

import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.gateway.PaymentGateway;

/**
 * 테스트용 PaymentGateway 구현체
 * - 결제 성공/실패 시나리오를 제어할 수 있음
 */
public class TestPaymentGateway implements PaymentGateway {

    private boolean shouldFail = false;
    private String failureMessage = "Test PG Failure";

    @Override
    public void processPayment(Payment payment) {
        if (shouldFail) {
            throw new RuntimeException(failureMessage);
        }
        // 성공 시 아무것도 하지 않음
    }

    /**
     * 다음 결제 호출 시 실패하도록 설정
     */
    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }

    /**
     * 실패 메시지 설정
     */
    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    /**
     * 상태 초기화 (성공 모드로 리셋)
     */
    public void reset() {
        this.shouldFail = false;
        this.failureMessage = "Test PG Failure";
    }
}
