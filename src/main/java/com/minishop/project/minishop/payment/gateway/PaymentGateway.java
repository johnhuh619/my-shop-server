package com.minishop.project.minishop.payment.gateway;

import com.minishop.project.minishop.payment.domain.Payment;

/**
 * 외부 결제 게이트웨이 인터페이스
 * - 실제 PG(Payment Gateway) 연동을 추상화
 * - 테스트 시 Mock으로 대체 가능
 */
public interface PaymentGateway {

    /**
     * 외부 결제 처리
     * @param payment 결제 정보
     * @throws RuntimeException 결제 실패 시
     */
    void processPayment(Payment payment);
}
