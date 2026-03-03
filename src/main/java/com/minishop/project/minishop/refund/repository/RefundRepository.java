package com.minishop.project.minishop.refund.repository;

import com.minishop.project.minishop.refund.domain.Refund;
import com.minishop.project.minishop.refund.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /**
     * 사용자의 환불 내역 조회
     */
    List<Refund> findByUserId(Long userId);

    /**
     * 환불 상세 조회 (소유권 확인용)
     */
    @Query("SELECT r FROM Refund r WHERE r.id = :refundId AND r.userId = :userId")
    Optional<Refund> findByIdAndUserId(@Param("refundId") Long refundId,
                                       @Param("userId") Long userId);

    /**
     * Payment 기준 환불 조회
     */
    List<Refund> findByPaymentId(Long paymentId);

    /**
     * Payment 기준 특정 상태의 환불 조회 (중복 환불 검증용)
     */
    @Query("SELECT r FROM Refund r LEFT JOIN FETCH r.refundItems " +
           "WHERE r.paymentId = :paymentId AND r.status IN :statuses")
    List<Refund> findByPaymentIdAndStatusIn(@Param("paymentId") Long paymentId,
                                            @Param("statuses") List<RefundStatus> statuses);

    /**
     * 상태별 환불 조회 (관리자용)
     */
    List<Refund> findByStatus(RefundStatus status);

    /**
     * Payment 기준 완료된 환불 금액 합계
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r " +
           "WHERE r.paymentId = :paymentId AND r.status = :status")
    Long sumAmountByPaymentIdAndStatus(@Param("paymentId") Long paymentId,
                                       @Param("status") RefundStatus status);

    /**
     * 주문 기준 환불 완료된 OrderItem ID 조회
     */
    @Query("SELECT ri.orderItemId FROM Refund r JOIN r.refundItems ri " +
           "WHERE r.orderId = :orderId AND r.status = com.minishop.project.minishop.refund.domain.RefundStatus.COMPLETED")
    Set<Long> findCompletedRefundItemIdsByOrderId(@Param("orderId") Long orderId);

    /**
     * 주문 ID 목록 기준 환불 완료된 OrderItem ID 배치 조회 (N+1 방지)
     * 각 row는 [orderId, orderItemId] 형태로 반환
     */
    @Query("SELECT r.orderId, ri.orderItemId FROM Refund r JOIN r.refundItems ri " +
           "WHERE r.orderId IN :orderIds AND r.status = com.minishop.project.minishop.refund.domain.RefundStatus.COMPLETED")
    List<Object[]> findCompletedRefundItemIdsByOrderIds(@Param("orderIds") List<Long> orderIds);
}
