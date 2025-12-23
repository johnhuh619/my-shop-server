package com.minishop.project.minishop.payment.repository;

import com.minishop.project.minishop.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 멱등성 체크를 위한 조회
     * (user_id, idempotency_key)는 UNIQUE 제약으로 보장됨
     */
    Optional<Payment> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /**
     * 사용자의 결제 내역 조회
     */
    List<Payment> findByUserId(Long userId);

    /**
     * 결제 상세 조회 (소유권 확인용)
     */
    @Query("SELECT p FROM Payment p WHERE p.id = :paymentId AND p.userId = :userId")
    Optional<Payment> findByIdAndUserId(@Param("paymentId") Long paymentId,
                                        @Param("userId") Long userId);
}
