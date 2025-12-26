package com.minishop.project.minishop.inventory.domain;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Inventory 도메인 불변식 테스트 (순수 단위 테스트)
 * - Spring Context 없음
 * - 도메인 로직 검증에 집중
 */
class InventoryTest {

    @Test
    void create_성공시_초기값설정() {
        // When
        Inventory inventory = Inventory.create(1L, 10L);

        // Then
        assertThat(inventory.getProductId()).isEqualTo(1L);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(10L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
        assertThat(inventory.getTotalQuantity()).isEqualTo(10L);
    }

    @Test
    void reserve_성공시_available감소_reserved증가() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);

        // When
        inventory.reserve(5L);

        // Then
        assertThat(inventory.getQuantityAvailable()).isEqualTo(5L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(5L);
        assertThat(inventory.getTotalQuantity()).isEqualTo(10L); // Total은 불변
    }

    @Test
    void reserve_available초과시_예외발생() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);

        // When & Then
        assertThatThrownBy(() -> inventory.reserve(11L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_INVENTORY);
    }

    @Test
    void reserve_available정확히소진_성공() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);

        // When
        inventory.reserve(10L);

        // Then
        assertThat(inventory.getQuantityAvailable()).isEqualTo(0L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(10L);
    }

    @Test
    void release_성공시_reserved감소_available증가() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);
        inventory.reserve(5L);

        // When
        inventory.release(3L);

        // Then
        assertThat(inventory.getQuantityAvailable()).isEqualTo(8L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(2L);
        assertThat(inventory.getTotalQuantity()).isEqualTo(10L);
    }

    @Test
    void release_reserved초과시_예외발생() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);
        inventory.reserve(5L);

        // When & Then
        assertThatThrownBy(() -> inventory.release(6L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("Cannot release more than reserved");
    }

    @Test
    void release_reserved없이호출시_예외발생() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);

        // When & Then
        assertThatThrownBy(() -> inventory.release(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("Cannot release more than reserved");
    }

    @Test
    void confirm_성공시_reserved감소() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);
        inventory.reserve(5L);

        // When
        inventory.confirm(3L);

        // Then
        assertThat(inventory.getQuantityAvailable()).isEqualTo(5L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(2L);
        assertThat(inventory.getTotalQuantity()).isEqualTo(7L); // 실제 소비됨
    }

    @Test
    void confirm_reserved초과시_예외발생() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);
        inventory.reserve(5L);

        // When & Then
        assertThatThrownBy(() -> inventory.confirm(6L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("Cannot confirm more than reserved");
    }

    @Test
    void confirm_reserved없이호출시_예외발생() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);

        // When & Then
        assertThatThrownBy(() -> inventory.confirm(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void addStock_성공시_available증가() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);

        // When
        inventory.addStock(5L);

        // Then
        assertThat(inventory.getQuantityAvailable()).isEqualTo(15L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
        assertThat(inventory.getTotalQuantity()).isEqualTo(15L);
    }

    @Test
    void getTotalQuantity_계산정확성() {
        // Given
        Inventory inventory = Inventory.create(1L, 100L);

        // When
        inventory.reserve(30L);  // available=70, reserved=30
        inventory.addStock(20L); // available=90, reserved=30

        // Then
        assertThat(inventory.getTotalQuantity()).isEqualTo(120L);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(90L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(30L);
    }

    @Test
    void 복합시나리오_예약후취소_원복확인() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);

        // When
        inventory.reserve(5L);
        inventory.release(5L);

        // Then
        assertThat(inventory.getQuantityAvailable()).isEqualTo(10L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
    }

    @Test
    void 복합시나리오_예약후확정_재고감소확인() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);

        // When
        inventory.reserve(5L);
        inventory.confirm(5L);

        // Then
        assertThat(inventory.getQuantityAvailable()).isEqualTo(5L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
        assertThat(inventory.getTotalQuantity()).isEqualTo(5L);
    }

    @Test
    void 복합시나리오_부분취소_부분확정() {
        // Given
        Inventory inventory = Inventory.create(1L, 10L);
        inventory.reserve(8L);

        // When
        inventory.release(3L); // 3개 취소
        inventory.confirm(5L); // 5개 확정

        // Then
        assertThat(inventory.getQuantityAvailable()).isEqualTo(5L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
        assertThat(inventory.getTotalQuantity()).isEqualTo(5L);
    }
}
