package com.minishop.project.minishop.payment.gateway;

import com.minishop.project.minishop.payment.dto.TossCancelResponse;
import com.minishop.project.minishop.payment.dto.TossConfirmResponse;

/**
 * 외부 결제 게이트웨이 인터페이스
 * - 토스페이먼츠 PG 연동을 추상화
 * - 테스트 시 Mock으로 대체 가능
 */
public interface PaymentGateway {

    /**
     * 결제 승인 요청
     * @param paymentKey 토스 결제 인증 후 발급된 키
     * @param tossOrderId 토스 호환 주문 ID
     * @param amount 결제 금액
     * @return 토스 승인 응답
     * @throws RuntimeException 결제 승인 실패 시
     */
    TossConfirmResponse confirmPayment(String paymentKey, String tossOrderId, Long amount);

    /**
     * 결제 취소(환불) 요청
     * @param paymentKey 토스 결제 키
     * @param cancelReason 취소 사유
     * @param cancelAmount 취소 금액 (null이면 전액 취소)
     * @return 토스 취소 응답
     * @throws RuntimeException 취소 실패 시
     */
    TossCancelResponse cancelPayment(String paymentKey, String cancelReason, Long cancelAmount);
}
