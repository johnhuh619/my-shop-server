package com.minishop.project.minishop.order.domain;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Order 도메인 상태 전이 테스트 (순수 단위 테스트)
 * - Spring Context 없음
 * - 상태 머신 검증에 집중
 */
class OrderTest {

    @Test
    void create_성공시_초기상태_CREATED() {
        // Given
        OrderItem item1 = OrderItem.create(1L, "Product A", 1000L, 2L);
        OrderItem item2 = OrderItem.create(2L, "Product B", 2000L, 3L);

        // When
        Order order = Order.create(100L, List.of(item1, item2));

        // Then
        assertThat(order.getUserId()).isEqualTo(100L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount()).isEqualTo(8000L); // 1000*2 + 2000*3
        assertThat(order.getOrderItems()).hasSize(2);
    }

    @Test
    void create_아이템없이생성시_총액0() {
        // When
        Order order = Order.create(100L, List.of());

        // Then
        assertThat(order.getTotalAmount()).isEqualTo(0L);
        assertThat(order.getOrderItems()).isEmpty();
    }

    // ============================================
    // CREATED 상태 전이 테스트
    // ============================================

    @Test
    void markAsPaid_CREATED에서PAID로_전이성공() {
        // Given
        Order order = createOrderWithItems();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        // When
        order.markAsPaid();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void cancel_CREATED에서CANCELED로_전이성공() {
        // Given
        Order order = createOrderWithItems();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        // When
        order.cancel();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void expire_CREATED에서EXPIRED로_전이성공() {
        // Given
        Order order = createOrderWithItems();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        // When
        order.expire();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    // ============================================
    // PAID 상태 전이 테스트
    // ============================================

    @Test
    void markAsPaid_PAID상태에서_예외발생() {
        // Given
        Order order = createOrderWithItems();
        order.markAsPaid();

        // When & Then
        assertThatThrownBy(() -> order.markAsPaid())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
                .hasMessageContaining("can only be marked as paid when status is CREATED");
    }

    @Test
    void cancel_PAID상태에서_예외발생() {
        // Given
        Order order = createOrderWithItems();
        order.markAsPaid();

        // When & Then
        assertThatThrownBy(() -> order.cancel())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
                .hasMessageContaining("can only be canceled when status is CREATED");
    }

    @Test
    void complete_PAID에서COMPLETED로_전이성공() {
        // Given
        Order order = createOrderWithItems();
        order.markAsPaid();

        // When
        order.complete();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void requestRefund_PAID에서REFUND_REQUESTED로_전이성공() {
        // Given
        Order order = createOrderWithItems();
        order.markAsPaid();

        // When
        order.requestRefund();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    }

    // ============================================
    // REFUND_REQUESTED 상태 전이 테스트
    // ============================================

    @Test
    void markAsRefunded_REFUND_REQUESTED에서REFUNDED로_전이성공() {
        // Given
        Order order = createOrderWithItems();
        order.markAsPaid();
        order.requestRefund();

        // When
        order.markAsRefunded();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void markAsRefunded_PAID상태에서_예외발생() {
        // Given
        Order order = createOrderWithItems();
        order.markAsPaid();

        // When & Then
        assertThatThrownBy(() -> order.markAsRefunded())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
                .hasMessageContaining("can only be marked as refunded when status is REFUND_REQUESTED");
    }

    // ============================================
    // 잘못된 상태 전이 테스트
    // ============================================

    @Test
    void complete_CREATED상태에서_예외발생() {
        // Given
        Order order = createOrderWithItems();

        // When & Then
        assertThatThrownBy(() -> order.complete())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
                .hasMessageContaining("can only be completed when status is PAID");
    }

    @Test
    void complete_COMPLETED상태에서_예외발생() {
        // Given
        Order order = createOrderWithItems();
        order.markAsPaid();
        order.complete();

        // When & Then
        assertThatThrownBy(() -> order.complete())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void requestRefund_CREATED상태에서_예외발생() {
        // Given
        Order order = createOrderWithItems();

        // When & Then
        assertThatThrownBy(() -> order.requestRefund())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
                .hasMessageContaining("can only be requested when order status is PAID");
    }

    @Test
    void requestRefund_CANCELED상태에서_예외발생() {
        // Given
        Order order = createOrderWithItems();
        order.cancel();

        // When & Then
        assertThatThrownBy(() -> order.requestRefund())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void expire_PAID상태에서_예외발생() {
        // Given
        Order order = createOrderWithItems();
        order.markAsPaid();

        // When & Then
        assertThatThrownBy(() -> order.expire())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
                .hasMessageContaining("can only be expired when status is CREATED");
    }

    @Test
    void cancel_CANCELED상태에서_예외발생() {
        // Given
        Order order = createOrderWithItems();
        order.cancel();

        // When & Then
        assertThatThrownBy(() -> order.cancel())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }

    // ============================================
    // 총액 계산 테스트
    // ============================================

    @Test
    void totalAmount_여러아이템_정확히계산() {
        // Given
        OrderItem item1 = OrderItem.create(1L, "Product A", 1500L, 3L);
        OrderItem item2 = OrderItem.create(2L, "Product B", 2500L, 2L);
        OrderItem item3 = OrderItem.create(3L, "Product C", 3000L, 1L);

        // When
        Order order = Order.create(100L, List.of(item1, item2, item3));

        // Then
        // 1500*3 + 2500*2 + 3000*1 = 4500 + 5000 + 3000 = 12500
        assertThat(order.getTotalAmount()).isEqualTo(12500L);
    }

    @Test
    void totalAmount_단일아이템_정확히계산() {
        // Given
        OrderItem item = OrderItem.create(1L, "Product A", 1000L, 5L);

        // When
        Order order = Order.create(100L, List.of(item));

        // Then
        assertThat(order.getTotalAmount()).isEqualTo(5000L);
    }

    // ============================================
    // 상태 전이 시나리오 테스트
    // ============================================

    @Test
    void 정상플로우_CREATED_PAID_COMPLETED() {
        // Given
        Order order = createOrderWithItems();

        // When & Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        order.markAsPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

        order.complete();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void 환불플로우_CREATED_PAID_REFUND_REQUESTED_REFUNDED() {
        // Given
        Order order = createOrderWithItems();

        // When & Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        order.markAsPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

        order.requestRefund();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);

        order.markAsRefunded();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void 취소플로우_CREATED_CANCELED() {
        // Given
        Order order = createOrderWithItems();

        // When & Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        order.cancel();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void 만료플로우_CREATED_EXPIRED() {
        // Given
        Order order = createOrderWithItems();

        // When & Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        order.expire();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    // ============================================
    // Helper Methods
    // ============================================

    private Order createOrderWithItems() {
        OrderItem item = OrderItem.create(1L, "Test Product", 1000L, 2L);
        return Order.create(100L, List.of(item));
    }
}
