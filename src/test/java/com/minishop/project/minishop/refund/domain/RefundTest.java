package com.minishop.project.minishop.refund.domain;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Refund 도메인 단위 테스트
 * - 상태 전이 테스트 (REQUESTED -> APPROVED/REJECTED -> COMPLETED/FAILED)
 * - 스냅샷 불변성 테스트
 * - Spring Context 없음
 */
class RefundTest {

    // ============================================
    // 헬퍼 메서드
    // ============================================

    private RefundItem createRefundItem(Long orderItemId, Long productId,
                                        String productName, Long unitPrice, Long quantity) {
        return RefundItem.create(orderItemId, productId, productName, unitPrice, quantity);
    }

    private List<RefundItem> createSingleRefundItem() {
        return List.of(createRefundItem(1L, 100L, "상품A", 10000L, 2L));
    }

    private List<RefundItem> createMultipleRefundItems() {
        return List.of(
                createRefundItem(1L, 100L, "상품A", 10000L, 2L),
                createRefundItem(2L, 200L, "상품B", 5000L, 3L)
        );
    }

    // ============================================
    // 생성 테스트
    // ============================================

    @Test
    void create_성공시_초기상태_REQUESTED() {
        // Given
        List<RefundItem> items = createSingleRefundItem();

        // When
        Refund refund = Refund.create(100L, 1L, 2L, items, "고객 요청");

        // Then
        assertThat(refund.getUserId()).isEqualTo(100L);
        assertThat(refund.getPaymentId()).isEqualTo(1L);
        assertThat(refund.getOrderId()).isEqualTo(2L);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getReason()).isEqualTo("고객 요청");
        assertThat(refund.getRefundItems()).hasSize(1);
    }

    @Test
    void create_RefundItem으로_금액자동계산() {
        // Given: 10000 * 2 + 5000 * 3 = 35000
        List<RefundItem> items = createMultipleRefundItems();

        // When
        Refund refund = Refund.create(100L, 1L, 2L, items, "환불 사유");

        // Then: 자동 계산된 금액 확인
        assertThat(refund.getAmount()).isEqualTo(35000L);
        assertThat(refund.getRefundItems()).hasSize(2);
        assertThat(refund.getCreatedAt()).isNotNull();
        assertThat(refund.getUpdatedAt()).isNotNull();
    }

    // ============================================
    // REQUESTED -> APPROVED 상태 전이 테스트
    // ============================================

    @Test
    void approve_REQUESTED에서APPROVED로_전이성공() {
        // Given
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "reason");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);

        // When
        refund.approve("승인합니다");

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(refund.getAdminComment()).isEqualTo("승인합니다");
    }

    @Test
    void reject_REQUESTED에서REJECTED로_전이성공() {
        // Given
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "reason");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);

        // When
        refund.reject("거절합니다");

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(refund.getAdminComment()).isEqualTo("거절합니다");
    }

    // ============================================
    // APPROVED -> COMPLETED/FAILED 상태 전이 테스트
    // ============================================

    @Test
    void markAsCompleted_APPROVED에서COMPLETED로_전이성공() {
        // Given
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "reason");
        refund.approve("승인");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);

        // When
        refund.markAsCompleted();

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    void markAsFailed_APPROVED에서FAILED로_전이성공() {
        // Given
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "reason");
        refund.approve("승인");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);

        // When
        refund.markAsFailed();

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
    }

    // ============================================
    // 잘못된 상태 전이 테스트
    // ============================================

    @Test
    void markAsCompleted_REQUESTED상태에서_예외발생() {
        // Given
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "reason");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);

        // When & Then
        assertThatThrownBy(() -> refund.markAsCompleted())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFUND_STATUS);
    }

    @Test
    void approve_APPROVED상태에서_예외발생() {
        // Given
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "reason");
        refund.approve("승인");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);

        // When & Then
        assertThatThrownBy(() -> refund.approve("다시 승인"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFUND_STATUS);
    }

    @Test
    void reject_APPROVED상태에서_예외발생() {
        // Given
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "reason");
        refund.approve("승인");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);

        // When & Then
        assertThatThrownBy(() -> refund.reject("거절"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFUND_STATUS);
    }

    @Test
    void markAsCompleted_COMPLETED상태에서_예외발생() {
        // Given
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "reason");
        refund.approve("승인");
        refund.markAsCompleted();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);

        // When & Then
        assertThatThrownBy(() -> refund.markAsCompleted())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFUND_STATUS);
    }

    // ============================================
    // 시나리오 테스트
    // ============================================

    @Test
    void 정상플로우_REQUESTED_APPROVED_COMPLETED() {
        // Given
        List<RefundItem> items = createMultipleRefundItems();
        Refund refund = Refund.create(100L, 1L, 2L, items, "정상 환불");

        // When: 관리자 승인 후 완료
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        refund.approve("승인합니다");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
        refund.markAsCompleted();

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getAmount()).isEqualTo(35000L);
    }

    @Test
    void 실패플로우_REQUESTED_APPROVED_FAILED() {
        // Given
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "실패 환불");

        // When: 관리자 승인 후 외부 처리 실패
        refund.approve("승인");
        refund.markAsFailed();

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getAmount()).isEqualTo(20000L);
    }

    @Test
    void 거절플로우_REQUESTED_REJECTED() {
        // Given
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "거절될 환불");

        // When: 관리자 거절
        refund.reject("상품 상태 불량");

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(refund.getAdminComment()).isEqualTo("상품 상태 불량");
    }

    // ============================================
    // 스냅샷 테스트
    // ============================================

    @Test
    void amount_스냅샷_불변성확인() {
        // Given: 환불 생성 (10000 * 2 = 20000)
        Refund refund = Refund.create(100L, 1L, 2L, createSingleRefundItem(), "reason");
        Long originalAmount = refund.getAmount();

        // When: 상태 전이 (금액은 변하지 않아야 함)
        refund.approve("승인");
        refund.markAsCompleted();

        // Then: 금액 스냅샷 유지
        assertThat(refund.getAmount()).isEqualTo(originalAmount);
    }

    @Test
    void RefundItem_스냅샷데이터확인() {
        // Given
        RefundItem item = createRefundItem(1L, 100L, "테스트상품", 15000L, 3L);
        Refund refund = Refund.create(100L, 1L, 2L, List.of(item), "reason");

        // Then: RefundItem 스냅샷 데이터 확인
        RefundItem savedItem = refund.getRefundItems().get(0);
        assertThat(savedItem.getOrderItemId()).isEqualTo(1L);
        assertThat(savedItem.getProductId()).isEqualTo(100L);
        assertThat(savedItem.getProductName()).isEqualTo("테스트상품");
        assertThat(savedItem.getUnitPrice()).isEqualTo(15000L);
        assertThat(savedItem.getQuantity()).isEqualTo(3L);
        assertThat(savedItem.getSubtotal()).isEqualTo(45000L);
    }

    @Test
    void 여러Refund_각각독립적인스냅샷() {
        // Given: 서로 다른 금액으로 여러 Refund 생성
        Refund refund1 = Refund.create(100L, 1L, 2L,
                List.of(createRefundItem(1L, 100L, "A", 10000L, 1L)), "환불1");
        Refund refund2 = Refund.create(100L, 3L, 4L,
                List.of(createRefundItem(2L, 200L, "B", 20000L, 1L)), "환불2");
        Refund refund3 = Refund.create(100L, 5L, 6L,
                List.of(createRefundItem(3L, 300L, "C", 30000L, 1L)), "환불3");

        // When: 각각 다른 상태로 전이
        refund1.approve("승인1");
        refund1.markAsCompleted();

        refund2.approve("승인2");
        refund2.markAsFailed();
        // refund3는 REQUESTED 유지

        // Then: 각 스냅샷 독립성 확인
        assertThat(refund1.getAmount()).isEqualTo(10000L);
        assertThat(refund1.getStatus()).isEqualTo(RefundStatus.COMPLETED);

        assertThat(refund2.getAmount()).isEqualTo(20000L);
        assertThat(refund2.getStatus()).isEqualTo(RefundStatus.FAILED);

        assertThat(refund3.getAmount()).isEqualTo(30000L);
        assertThat(refund3.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }
}
