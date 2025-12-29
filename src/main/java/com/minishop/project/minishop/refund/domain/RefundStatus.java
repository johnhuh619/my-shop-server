package com.minishop.project.minishop.refund.domain;

public enum RefundStatus {
    REQUESTED,      // 환불 요청됨 (사용자가 요청)
    APPROVED,       // 관리자 승인
    REJECTED,       // 관리자 거절
    COMPLETED,      // 환불 완료 (외부 처리 성공)
    FAILED          // 환불 실패 (외부 처리 실패)
}
