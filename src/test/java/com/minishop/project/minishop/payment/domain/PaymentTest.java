package com.minishop.project.minishop.payment.domain;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Payment 도메인 상태 전이 테스트 (순수 단위 테스트)
 * - Spring Context 없음
 * - 상태 머신 검증에 집중
 */
class PaymentTest {

    // ============================================
    // 생성 테스트
    // ============================================

    @Test
    void create_성공시_초기상태_REQUESTED() {
        // When
        Payment payment = Payment.create(100L, 1L, "idempotency-key-123", 50000L);

        // Then
        assertThat(payment.getUserId()).isEqualTo(100L);
        assertThat(payment.getOrderId()).isEqualTo(1L);
        assertThat(payment.getIdempotencyKey()).isEqualTo("idempotency-key-123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getAmount()).isEqualTo(50000L);
        assertThat(payment.getCreatedAt()).isNotNull();
        assertThat(payment.getUpdatedAt()).isNotNull();
    }

    @Test
    void create_성공시_스냅샷설정() {
        // When
        Payment payment = Payment.create(200L, 5L, "key-456", 99999L);

        // Then: 스냅샷 필드 모두 설정됨
        assertThat(payment.getUserId()).isEqualTo(200L);
        assertThat(payment.getOrderId()).isEqualTo(5L);
        assertThat(payment.getIdempotencyKey()).isEqualTo("key-456");
        assertThat(payment.getAmount()).isEqualTo(99999L);
    }

    // ============================================
    // REQUESTED 상태 전이 테스트
    // ============================================

    @Test
    void markAsCompleted_REQUESTED에서COMPLETED로_전이성공() {
        // Given
        Payment payment = Payment.create(100L, 1L, "key", 10000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        // When
        payment.markAsCompleted();

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void markAsFailed_REQUESTED에서FAILED로_전이성공() {
        // Given
        Payment payment = Payment.create(100L, 1L, "key", 10000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        // When
        payment.markAsFailed();

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // ============================================
    // COMPLETED 상태 전이 테스트
    // ============================================

    @Test
    void markAsCompleted_COMPLETED상태에서_예외발생() {
        // Given
        Payment payment = Payment.create(100L, 1L, "key", 10000L);
        payment.markAsCompleted();

        // When & Then
        assertThatThrownBy(() -> payment.markAsCompleted())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
                .hasMessageContaining("can only be completed when status is REQUESTED");
    }

    @Test
    void markAsFailed_COMPLETED상태에서_예외발생() {
        // Given
        Payment payment = Payment.create(100L, 1L, "key", 10000L);
        payment.markAsCompleted();

        // When & Then
        assertThatThrownBy(() -> payment.markAsFailed())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
                .hasMessageContaining("can only be failed when status is REQUESTED");
    }

    // ============================================
    // FAILED 상태 전이 테스트
    // ============================================

    @Test
    void markAsCompleted_FAILED상태에서_예외발생() {
        // Given
        Payment payment = Payment.create(100L, 1L, "key", 10000L);
        payment.markAsFailed();

        // When & Then
        assertThatThrownBy(() -> payment.markAsCompleted())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
                .hasMessageContaining("can only be completed when status is REQUESTED");
    }

    @Test
    void markAsFailed_FAILED상태에서_예외발생() {
        // Given
        Payment payment = Payment.create(100L, 1L, "key", 10000L);
        payment.markAsFailed();

        // When & Then
        assertThatThrownBy(() -> payment.markAsFailed())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }

    // ============================================
    // 상태 전이 시나리오 테스트
    // ============================================

    @Test
    void 정상플로우_REQUESTED_COMPLETED() {
        // Given
        Payment payment = Payment.create(100L, 1L, "key", 10000L);

        // When & Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        payment.markAsCompleted();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void 실패플로우_REQUESTED_FAILED() {
        // Given
        Payment payment = Payment.create(100L, 1L, "key", 10000L);

        // When & Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        payment.markAsFailed();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // ============================================
    // 스냅샷 데이터 테스트
    // ============================================

    @Test
    void amount_스냅샷_불변성확인() {
        // Given
        Long originalAmount = 10000L;
        Payment payment = Payment.create(100L, 1L, "key", originalAmount);

        // When: 결제 완료
        payment.markAsCompleted();

        // Then: amount는 변경되지 않음
        assertThat(payment.getAmount()).isEqualTo(originalAmount);
    }

    @Test
    void 여러Payment_각각독립적인스냅샷() {
        // When
        Payment payment1 = Payment.create(100L, 1L, "key1", 10000L);
        Payment payment2 = Payment.create(100L, 2L, "key2", 20000L);

        // Then: 각 결제는 독립적인 금액 스냅샷
        assertThat(payment1.getAmount()).isEqualTo(10000L);
        assertThat(payment2.getAmount()).isEqualTo(20000L);
        assertThat(payment1.getIdempotencyKey()).isEqualTo("key1");
        assertThat(payment2.getIdempotencyKey()).isEqualTo("key2");
    }
}
