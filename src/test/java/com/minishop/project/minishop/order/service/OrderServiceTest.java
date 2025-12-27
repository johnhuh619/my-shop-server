package com.minishop.project.minishop.order.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.domain.Inventory;
import com.minishop.project.minishop.inventory.repository.InventoryRepository;
import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.dto.OrderItemRequest;
import com.minishop.project.minishop.order.repository.OrderRepository;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.domain.ProductStatus;
import com.minishop.project.minishop.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * OrderService 통합 테스트
 * - 트랜잭션 경계 검증
 * - 서비스 간 협력 검증
 * - 소유권 검증
 */
@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    private Long testUserId = 999L;
    private Long otherUserId = 888L;

    @AfterEach
    void tearDown() {
        // @Transactional이 롤백하므로 수동 정리 불필요
    }

    // ============================================
    // 주문 생성 테스트
    // ============================================

    @Test
    void createOrder_성공시_재고예약됨() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);

        // When
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount()).isEqualTo(50000L);
        assertThat(order.getOrderItems()).hasSize(1);

        // Then: 재고 확인
        Inventory inventory = inventoryService.getByProductId(product.getId());
        assertThat(inventory.getQuantityAvailable()).isEqualTo(5L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(5L);
    }

    @Test
    void createOrder_여러상품_재고예약됨() {
        // Given
        Product product1 = createProduct("Product A", 1000L);
        Product product2 = createProduct("Product B", 2000L);
        inventoryService.addStock(product1.getId(), 100L);
        inventoryService.addStock(product2.getId(), 50L);

        // When
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product1.getId(), 3L),
                new OrderItemRequest(product2.getId(), 2L)
        ));

        // Then
        assertThat(order.getTotalAmount()).isEqualTo(7000L); // 1000*3 + 2000*2
        assertThat(order.getOrderItems()).hasSize(2);

        // Then: 재고 확인
        Inventory inventory1 = inventoryService.getByProductId(product1.getId());
        assertThat(inventory1.getQuantityReserved()).isEqualTo(3L);

        Inventory inventory2 = inventoryService.getByProductId(product2.getId());
        assertThat(inventory2.getQuantityReserved()).isEqualTo(2L);
    }

    @Test
    void createOrder_재고부족시_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 5L);

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 10L)
        )))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_INVENTORY);

        // Then: 재고 변화 없음 (트랜잭션 롤백)
        Inventory inventory = inventoryService.getByProductId(product.getId());
        assertThat(inventory.getQuantityAvailable()).isEqualTo(5L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
    }

    @Test
    void createOrder_상품없음_예외발생() {
        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(999L, 1L)
        )))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void createOrder_아이템없음_예외발생() {
        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(testUserId, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("Order must have at least one item");
    }

    @Test
    void createOrder_수량0_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 0L)
        )))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("Quantity must be positive");
    }

    // ============================================
    // 주문 취소 테스트
    // ============================================

    @Test
    void cancelOrder_성공시_재고해제됨() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));

        // When
        Order canceledOrder = orderService.cancelOrder(order.getId(), testUserId);

        // Then
        assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);

        // Then: 재고 해제 확인
        Inventory inventory = inventoryService.getByProductId(product.getId());
        assertThat(inventory.getQuantityAvailable()).isEqualTo(10L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
    }

    @Test
    void cancelOrder_여러상품_모두재고해제됨() {
        // Given
        Product product1 = createProduct("Product A", 1000L);
        Product product2 = createProduct("Product B", 2000L);
        inventoryService.addStock(product1.getId(), 100L);
        inventoryService.addStock(product2.getId(), 50L);

        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product1.getId(), 3L),
                new OrderItemRequest(product2.getId(), 2L)
        ));

        // When
        orderService.cancelOrder(order.getId(), testUserId);

        // Then: 모든 재고 해제 확인
        Inventory inventory1 = inventoryService.getByProductId(product1.getId());
        assertThat(inventory1.getQuantityAvailable()).isEqualTo(100L);
        assertThat(inventory1.getQuantityReserved()).isEqualTo(0L);

        Inventory inventory2 = inventoryService.getByProductId(product2.getId());
        assertThat(inventory2.getQuantityAvailable()).isEqualTo(50L);
        assertThat(inventory2.getQuantityReserved()).isEqualTo(0L);
    }

    // ============================================
    // 소유권 검증 테스트
    // ============================================

    @Test
    void getOrder_타인주문조회_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));

        // When & Then
        assertThatThrownBy(() -> orderService.getOrder(order.getId(), otherUserId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void cancelOrder_타인주문취소_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));

        // When & Then
        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), otherUserId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);

        // Then: 주문 상태 변화 없음
        Order unchangedOrder = orderService.getOrder(order.getId(), testUserId);
        assertThat(unchangedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void getOrder_본인주문조회_성공() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));

        // When
        Order retrievedOrder = orderService.getOrder(order.getId(), testUserId);

        // Then
        assertThat(retrievedOrder.getId()).isEqualTo(order.getId());
        assertThat(retrievedOrder.getUserId()).isEqualTo(testUserId);
    }

    // ============================================
    // 주문 완료 테스트
    // ============================================

    @Test
    void completeOrder_성공시_재고확정됨() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));

        // When: CREATED → PAID → COMPLETED
        orderService.markAsPaid(order.getId());
        Order completedOrder = orderService.completeOrder(order.getId());

        // Then
        assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // Then: 재고 확정 확인 (reserved 감소, available 유지)
        Inventory inventory = inventoryService.getByProductId(product.getId());
        assertThat(inventory.getQuantityAvailable()).isEqualTo(5L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
        assertThat(inventory.getTotalQuantity()).isEqualTo(5L); // 5개 소비됨
    }

    // ============================================
    // 주문 만료 테스트
    // ============================================

    @Test
    void expireOrder_성공시_재고해제됨() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));

        // When
        orderService.expireOrder(order.getId());

        // Then
        Order expiredOrder = orderRepository.findById(order.getId()).get();
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        // Then: 재고 해제 확인
        Inventory inventory = inventoryService.getByProductId(product.getId());
        assertThat(inventory.getQuantityAvailable()).isEqualTo(10L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
    }

    @Test
    void expireOrder_PAID상태_만료안됨() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));
        orderService.markAsPaid(order.getId());

        // When
        orderService.expireOrder(order.getId());

        // Then: 상태 변화 없음 (PAID 상태는 만료 불가)
        Order unchangedOrder = orderRepository.findById(order.getId()).get();
        assertThat(unchangedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    // ============================================
    // OrderItem 스냅샷 통합 테스트
    // ============================================

    @Test
    void createOrder_상품가격변경후_OrderItem가격불변() {
        // Given: 초기 상품 가격 10000원
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);

        // When: 주문 생성 (가격 10000원으로 스냅샷)
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));

        // When: 상품 가격 변경 (10000 → 20000)
        product.updateInfo("Test Product", "desc", 20000L);
        productRepository.save(product);

        // Then: OrderItem 가격은 변경되지 않음 (스냅샷 유지)
        Order retrievedOrder = orderService.getOrder(order.getId(), testUserId);
        assertThat(retrievedOrder.getOrderItems().get(0).getUnitPrice()).isEqualTo(10000L);
        assertThat(retrievedOrder.getOrderItems().get(0).getSubtotal()).isEqualTo(20000L); // 10000 * 2
        assertThat(retrievedOrder.getTotalAmount()).isEqualTo(20000L);

        // Then: Product는 새 가격으로 변경됨
        Product updatedProduct = productRepository.findById(product.getId()).get();
        assertThat(updatedProduct.getUnitPrice()).isEqualTo(20000L);
    }

    @Test
    void getOrdersByUser_본인주문만조회() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 100L);

        orderService.createOrder(testUserId, List.of(new OrderItemRequest(product.getId(), 1L)));
        orderService.createOrder(testUserId, List.of(new OrderItemRequest(product.getId(), 2L)));
        orderService.createOrder(otherUserId, List.of(new OrderItemRequest(product.getId(), 3L)));

        // When
        List<Order> userOrders = orderService.getOrdersByUser(testUserId);

        // Then
        assertThat(userOrders).hasSize(2);
        assertThat(userOrders).allMatch(order -> order.getUserId().equals(testUserId));
    }

    // ============================================
    // Helper Methods
    // ============================================

    private Product createProduct(String name, Long price) {
        Product product = Product.builder()
                .name(name)
                .description("Test Description")
                .unitPrice(price)
                .status(ProductStatus.ACTIVE)
                .build();
        Product saved = productRepository.save(product);

        // Product 생성 시 자동으로 Inventory 초기화
        inventoryService.initializeInventory(saved.getId());

        return saved;
    }
}
