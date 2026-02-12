package com.minishop.project.minishop.payment.repository;

import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * 토스 주문 ID로 결제 조회 (confirm 시 사용)
     */
    Optional<Payment> findByTossOrderId(String tossOrderId);

    /**
     * 토스 주문 ID로 결제 조회 + PESSIMISTIC_WRITE lock (confirm 동시성 보호)
     */
    @Query("SELECT p FROM Payment p WHERE p.tossOrderId = :tossOrderId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByTossOrderIdWithLock(@Param("tossOrderId") String tossOrderId);

    /**
     * 보상 스케줄러용: 특정 상태이면서 updatedAt이 기준 시각보다 이전인 결제 조회
     */
    List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, Instant updatedAt);
}
