package com.minishop.project.minishop.order.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * OrderItem 스냅샷 불변성 테스트 (순수 단위 테스트)
 * - Spring Context 없음
 * - 스냅샷 데이터 불변 검증에 집중
 */
class OrderItemTest {

    @Test
    void create_성공시_스냅샷데이터_설정() {
        // When
        OrderItem item = OrderItem.create(1L, "Test Product", 1000L, 5L);

        // Then
        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getProductName()).isEqualTo("Test Product");
        assertThat(item.getUnitPrice()).isEqualTo(1000L);
        assertThat(item.getQuantity()).isEqualTo(5L);
    }

    @Test
    void getSubtotal_계산정확성() {
        // Given
        OrderItem item = OrderItem.create(1L, "Test Product", 1000L, 5L);

        // When
        Long subtotal = item.getSubtotal();

        // Then
        assertThat(subtotal).isEqualTo(5000L);
    }

    @Test
    void getSubtotal_다양한값_계산정확성() {
        // Given & When & Then
        assertThat(OrderItem.create(1L, "A", 100L, 1L).getSubtotal()).isEqualTo(100L);
        assertThat(OrderItem.create(2L, "B", 1500L, 3L).getSubtotal()).isEqualTo(4500L);
        assertThat(OrderItem.create(3L, "C", 2000L, 10L).getSubtotal()).isEqualTo(20000L);
        assertThat(OrderItem.create(4L, "D", 999L, 7L).getSubtotal()).isEqualTo(6993L);
    }

    @Test
    void getSubtotal_수량1_단가와동일() {
        // Given
        OrderItem item = OrderItem.create(1L, "Test Product", 5000L, 1L);

        // When
        Long subtotal = item.getSubtotal();

        // Then
        assertThat(subtotal).isEqualTo(5000L);
    }

    // ============================================
    // 스냅샷 불변성 검증 (Reflection 사용)
    // ============================================

    @Test
    void productName_setter없음_불변보장() {
        // Given
        Class<OrderItem> clazz = OrderItem.class;

        // When
        boolean hasPublicSetter = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .anyMatch(m -> m.getName().equals("setProductName"));

        // Then
        assertThat(hasPublicSetter).isFalse();
    }

    @Test
    void unitPrice_setter없음_불변보장() {
        // Given
        Class<OrderItem> clazz = OrderItem.class;

        // When
        boolean hasPublicSetter = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .anyMatch(m -> m.getName().equals("setUnitPrice"));

        // Then
        assertThat(hasPublicSetter).isFalse();
    }

    @Test
    void quantity_setter없음_불변보장() {
        // Given
        Class<OrderItem> clazz = OrderItem.class;

        // When
        boolean hasPublicSetter = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .anyMatch(m -> m.getName().equals("setQuantity"));

        // Then
        assertThat(hasPublicSetter).isFalse();
    }

    @Test
    void productId_setter없음_불변보장() {
        // Given
        Class<OrderItem> clazz = OrderItem.class;

        // When
        boolean hasPublicSetter = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .anyMatch(m -> m.getName().equals("setProductId"));

        // Then
        assertThat(hasPublicSetter).isFalse();
    }

    @Test
    void 스냅샷필드_final은아니지만_setter없음확인() {
        // Given
        OrderItem item = OrderItem.create(1L, "Original Product", 1000L, 5L);

        // When
        Class<OrderItem> clazz = OrderItem.class;
        Method[] publicMethods = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getName().startsWith("set"))
                .filter(m -> !m.getName().equals("setOrder")) // setOrder는 package-private
                .toArray(Method[]::new);

        // Then: setOrder를 제외하고 public setter가 없어야 함
        assertThat(publicMethods).isEmpty();
    }

    @Test
    void 스냅샷필드_Lombok_Getter만존재() {
        // Given
        Class<OrderItem> clazz = OrderItem.class;

        // When
        boolean hasGetProductName = Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("getProductName"));
        boolean hasGetUnitPrice = Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("getUnitPrice"));
        boolean hasGetQuantity = Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("getQuantity"));

        // Then
        assertThat(hasGetProductName).isTrue();
        assertThat(hasGetUnitPrice).isTrue();
        assertThat(hasGetQuantity).isTrue();
    }

    // ============================================
    // 스냅샷 데이터 시나리오 테스트
    // ============================================

    @Test
    void 여러OrderItem_각각독립적인스냅샷() {
        // Given & When
        OrderItem item1 = OrderItem.create(1L, "Product A", 1000L, 2L);
        OrderItem item2 = OrderItem.create(1L, "Product A", 1500L, 2L); // 같은 productId, 다른 가격

        // Then: 같은 상품이어도 주문 시점의 가격을 각각 스냅샷
        assertThat(item1.getProductId()).isEqualTo(item2.getProductId());
        assertThat(item1.getUnitPrice()).isNotEqualTo(item2.getUnitPrice());
        assertThat(item1.getSubtotal()).isEqualTo(2000L);
        assertThat(item2.getSubtotal()).isEqualTo(3000L);
    }

    @Test
    void 동일상품_다른수량_각각스냅샷() {
        // Given & When
        OrderItem item1 = OrderItem.create(1L, "Product A", 1000L, 2L);
        OrderItem item2 = OrderItem.create(1L, "Product A", 1000L, 5L);

        // Then
        assertThat(item1.getQuantity()).isEqualTo(2L);
        assertThat(item2.getQuantity()).isEqualTo(5L);
        assertThat(item1.getSubtotal()).isEqualTo(2000L);
        assertThat(item2.getSubtotal()).isEqualTo(5000L);
    }

    @Test
    void subtotal_계산로직_항상동일() {
        // Given
        OrderItem item = OrderItem.create(1L, "Test Product", 1234L, 56L);

        // When
        Long subtotal1 = item.getSubtotal();
        Long subtotal2 = item.getSubtotal();
        Long subtotal3 = item.getSubtotal();

        // Then: 여러 번 호출해도 항상 같은 값
        assertThat(subtotal1).isEqualTo(subtotal2).isEqualTo(subtotal3);
        assertThat(subtotal1).isEqualTo(1234L * 56L);
    }

    @Test
    void 큰금액_계산정확성() {
        // Given
        OrderItem item = OrderItem.create(1L, "Expensive Product", 999999L, 100L);

        // When
        Long subtotal = item.getSubtotal();

        // Then
        assertThat(subtotal).isEqualTo(99999900L);
    }

    @Test
    void 필드접근제어_확인() throws NoSuchFieldException {
        // Given
        Class<OrderItem> clazz = OrderItem.class;

        // When: 스냅샷 필드들의 접근 제어자 확인
        Field productNameField = clazz.getDeclaredField("productName");
        Field unitPriceField = clazz.getDeclaredField("unitPrice");
        Field quantityField = clazz.getDeclaredField("quantity");

        // Then: private 필드여야 함
        assertThat(Modifier.isPrivate(productNameField.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(unitPriceField.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(quantityField.getModifiers())).isTrue();
    }
}
