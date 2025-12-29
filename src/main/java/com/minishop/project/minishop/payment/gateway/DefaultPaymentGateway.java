package com.minishop.project.minishop.payment.gateway;

import com.minishop.project.minishop.payment.domain.Payment;
import org.springframework.stereotype.Component;

/**
 * 기본 결제 게이트웨이 구현체
 * - 현재는 항상 성공하는 stub 구현
 * - TODO: 실제 PG 연동 로직 구현 필요
 */
@Component
public class DefaultPaymentGateway implements PaymentGateway {

    @Override
    public void processPayment(Payment payment) {
        // TODO: 외부 PG 연동 로직
        // 현재는 항상 성공으로 처리
        // 예: 토스페이먼츠, 아임포트 등 PG사 API 호출
    }
}
