package com.minishop.project.minishop.refund.repository;

import com.minishop.project.minishop.refund.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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
}
