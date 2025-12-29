package com.minishop.project.minishop.refund.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.domain.Inventory;
import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderItem;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.dto.OrderItemRequest;
import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.service.PaymentService;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.domain.ProductStatus;
import com.minishop.project.minishop.product.repository.ProductRepository;
import com.minishop.project.minishop.refund.domain.Refund;
import com.minishop.project.minishop.refund.domain.RefundStatus;
import com.minishop.project.minishop.refund.dto.RefundItemRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * RefundService 통합 테스트
 * - RefundItem 기반 환불 시스템
 * - 관리자 승인 프로세스
 * - 중복 환불 방지
 * - 재고 복구 검증
 */
@SpringBootTest
@Transactional
class RefundServiceTest {

    @Autowired
    private RefundService refundService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    private Long testUserId = 999L;
    private Long otherUserId = 888L;

    // ============================================
    // 기본 환불 요청 테스트
    // ============================================

    @Test
    void processRefund_환불요청_REQUESTED상태로생성() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key-123");

        OrderItem orderItem = order.getOrderItems().get(0);
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 2L);

        // When
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "환불 요청");

        // Then: REQUESTED 상태로 생성 (관리자 승인 대기)
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getUserId()).isEqualTo(testUserId);
        assertThat(refund.getPaymentId()).isEqualTo(payment.getId());
        assertThat(refund.getAmount()).isEqualTo(20000L); // 10000 * 2
        assertThat(refund.getRefundItems()).hasSize(1);
    }

    @Test
    void processRefund_Payment없음_예외발생() {
        // Given
        List<RefundItemRequest> items = createRefundItemRequests(1L, 1L);

        // When & Then
        assertThatThrownBy(() ->
                refundService.processRefund(testUserId, 9999L, items, "reason"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void processRefund_빈ItemList_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // When & Then
        assertThatThrownBy(() ->
                refundService.processRefund(testUserId, payment.getId(), List.of(), "reason"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    // ============================================
    // 관리자 승인/거절 테스트
    // ============================================

    @Test
    void approveRefund_승인후COMPLETED로전이() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        OrderItem orderItem = order.getOrderItems().get(0);
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 2L);
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "환불 요청");

        // When
        Refund approved = refundService.approveRefund(refund.getId(), "승인합니다");

        // Then
        assertThat(approved.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(approved.getAdminComment()).isEqualTo("승인합니다");
    }

    @Test
    void rejectRefund_거절후REJECTED로전이() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        OrderItem orderItem = order.getOrderItems().get(0);
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 2L);
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "환불 요청");

        // When
        Refund rejected = refundService.rejectRefund(refund.getId(), "거절합니다");

        // Then
        assertThat(rejected.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(rejected.getAdminComment()).isEqualTo("거절합니다");
    }

    // ============================================
    // 금액 자동 계산 테스트
    // ============================================

    @Test
    void processRefund_RefundItem으로금액자동계산() {
        // Given: 10000 * 3 = 30000
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)  // 50000원 주문
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        OrderItem orderItem = order.getOrderItems().get(0);
        // 5개 중 3개만 환불
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 3L);

        // When
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "부분 환불");

        // Then: 30000원 자동 계산
        assertThat(refund.getAmount()).isEqualTo(30000L);
    }

    @Test
    void processRefund_여러상품부분환불_금액계산() {
        // Given
        Product product1 = createProduct("Product A", 1000L);
        Product product2 = createProduct("Product B", 2000L);
        inventoryService.addStock(product1.getId(), 100L);
        inventoryService.addStock(product2.getId(), 50L);

        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product1.getId(), 5L),  // 5000원
                new OrderItemRequest(product2.getId(), 3L)   // 6000원, 총 11000원
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // product1 3개만 환불 요청
        OrderItem orderItem1 = order.getOrderItems().stream()
                .filter(item -> item.getProductId().equals(product1.getId()))
                .findFirst().orElseThrow();
        List<RefundItemRequest> items = createRefundItemRequests(orderItem1.getId(), 3L);

        // When
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "부분 환불");

        // Then: 1000 * 3 = 3000원
        assertThat(refund.getAmount()).isEqualTo(3000L);
    }

    // ============================================
    // 중복 환불 방지 테스트
    // ============================================

    @Test
    void processRefund_이미환불된수량초과_예외발생() {
        // Given: 5개 주문 후 3개 환불 요청
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        OrderItem orderItem = order.getOrderItems().get(0);

        // 첫 번째 환불: 3개
        List<RefundItemRequest> items1 = createRefundItemRequests(orderItem.getId(), 3L);
        refundService.processRefund(testUserId, payment.getId(), items1, "첫 번째 환불");

        // When & Then: 추가 3개 환불 시도 (총 6개 > 원본 5개)
        List<RefundItemRequest> items2 = createRefundItemRequests(orderItem.getId(), 3L);
        assertThatThrownBy(() ->
                refundService.processRefund(testUserId, payment.getId(), items2, "두 번째 환불"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_QUANTITY_EXCEEDED);
    }

    @Test
    void processRefund_다중환불_남은수량만환불가능() {
        // Given: 10개 주문
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 20L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 10L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        OrderItem orderItem = order.getOrderItems().get(0);

        // 첫 번째 환불: 3개
        List<RefundItemRequest> items1 = createRefundItemRequests(orderItem.getId(), 3L);
        Refund refund1 = refundService.processRefund(testUserId, payment.getId(), items1, "1차 환불");
        assertThat(refund1.getAmount()).isEqualTo(30000L);

        // 두 번째 환불: 5개 (남은 7개 중)
        List<RefundItemRequest> items2 = createRefundItemRequests(orderItem.getId(), 5L);
        Refund refund2 = refundService.processRefund(testUserId, payment.getId(), items2, "2차 환불");
        assertThat(refund2.getAmount()).isEqualTo(50000L);

        // 세 번째 환불: 2개 (남은 2개)
        List<RefundItemRequest> items3 = createRefundItemRequests(orderItem.getId(), 2L);
        Refund refund3 = refundService.processRefund(testUserId, payment.getId(), items3, "3차 환불");
        assertThat(refund3.getAmount()).isEqualTo(20000L);
    }

    @Test
    void processRefund_존재하지않는OrderItem_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // 존재하지 않는 OrderItem ID
        List<RefundItemRequest> items = createRefundItemRequests(99999L, 1L);

        // When & Then
        assertThatThrownBy(() ->
                refundService.processRefund(testUserId, payment.getId(), items, "reason"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_ITEM_NOT_FOUND);
    }

    // ============================================
    // 재고 복구 테스트 (RefundItem 기반)
    // ============================================

    @Test
    void approveRefund_승인시_RefundItem기반재고복구() {
        // Given: 10개 재고, 5개 주문
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // 5개 중 2개만 환불 요청
        OrderItem orderItem = order.getOrderItems().get(0);
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 2L);
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "환불");

        // 결제 후 available=5 (10-5=5)
        Inventory beforeApprove = inventoryService.getByProductId(product.getId());
        Long availableBefore = beforeApprove.getQuantityAvailable();

        // When: 관리자 승인
        refundService.approveRefund(refund.getId(), "승인");

        // Then: 2개만 재고 복구
        Inventory afterApprove = inventoryService.getByProductId(product.getId());
        assertThat(afterApprove.getQuantityAvailable()).isEqualTo(availableBefore + 2);
    }

    @Test
    void approveRefund_여러상품환불_각각정확한재고복구() {
        // Given
        Product product1 = createProduct("Product A", 1000L);
        Product product2 = createProduct("Product B", 2000L);
        inventoryService.addStock(product1.getId(), 100L);
        inventoryService.addStock(product2.getId(), 50L);

        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product1.getId(), 10L),  // 10개 주문
                new OrderItemRequest(product2.getId(), 5L)    // 5개 주문
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // product1 3개, product2 2개 환불 요청
        OrderItem orderItem1 = order.getOrderItems().stream()
                .filter(item -> item.getProductId().equals(product1.getId()))
                .findFirst().orElseThrow();
        OrderItem orderItem2 = order.getOrderItems().stream()
                .filter(item -> item.getProductId().equals(product2.getId()))
                .findFirst().orElseThrow();

        List<RefundItemRequest> items = List.of(
                createRefundItemRequest(orderItem1.getId(), 3L),
                createRefundItemRequest(orderItem2.getId(), 2L)
        );
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "환불");

        // When: 관리자 승인
        refundService.approveRefund(refund.getId(), "승인");

        // Then: 각각 정확한 수량만 복구
        // product1: 100 - 10 + 3 = 93
        // product2: 50 - 5 + 2 = 47
        Inventory inv1 = inventoryService.getByProductId(product1.getId());
        assertThat(inv1.getQuantityAvailable()).isEqualTo(93L);

        Inventory inv2 = inventoryService.getByProductId(product2.getId());
        assertThat(inv2.getQuantityAvailable()).isEqualTo(47L);
    }

    @Test
    void rejectRefund_거절시_재고복구안함() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        OrderItem orderItem = order.getOrderItems().get(0);
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 2L);
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "환불");

        Inventory beforeReject = inventoryService.getByProductId(product.getId());
        Long availableBefore = beforeReject.getQuantityAvailable();

        // When: 관리자 거절
        refundService.rejectRefund(refund.getId(), "거절");

        // Then: 재고 변화 없음
        Inventory afterReject = inventoryService.getByProductId(product.getId());
        assertThat(afterReject.getQuantityAvailable()).isEqualTo(availableBefore);
    }

    // ============================================
    // Order 상태 변화 테스트
    // ============================================

    @Test
    void approveRefund_전액환불시_Order상태REFUNDED() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 3L)  // 30000원
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // 전액 환불 (3개 모두)
        OrderItem orderItem = order.getOrderItems().get(0);
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 3L);
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "전액 환불");

        // When
        refundService.approveRefund(refund.getId(), "승인");

        // Then: Order 상태 REFUNDED
        Order refundedOrder = orderService.getOrderById(order.getId());
        assertThat(refundedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void approveRefund_부분환불시_Order상태PAID유지() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)  // 50000원
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // 부분 환불 (5개 중 2개)
        OrderItem orderItem = order.getOrderItems().get(0);
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 2L);
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "부분 환불");

        // When
        refundService.approveRefund(refund.getId(), "승인");

        // Then: Order 상태 REFUND_REQUESTED 유지 (부분 환불 - 아직 전액 환불 아님)
        Order partialRefundedOrder = orderService.getOrderById(order.getId());
        assertThat(partialRefundedOrder.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    }

    // ============================================
    // 소유권 테스트
    // ============================================

    @Test
    void getRefund_본인환불조회_성공() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        OrderItem orderItem = order.getOrderItems().get(0);
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 1L);
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "환불");

        // When
        Refund retrieved = refundService.getRefund(refund.getId(), testUserId);

        // Then
        assertThat(retrieved.getId()).isEqualTo(refund.getId());
        assertThat(retrieved.getUserId()).isEqualTo(testUserId);
    }

    @Test
    void getRefund_타인환불조회_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        OrderItem orderItem = order.getOrderItems().get(0);
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 1L);
        Refund refund = refundService.processRefund(testUserId, payment.getId(), items, "환불");

        // When & Then
        assertThatThrownBy(() ->
                refundService.getRefund(refund.getId(), otherUserId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_NOT_FOUND);
    }

    @Test
    void processRefund_타인결제환불_예외발생() {
        // Given: testUserId의 결제
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        OrderItem orderItem = order.getOrderItems().get(0);
        List<RefundItemRequest> items = createRefundItemRequests(orderItem.getId(), 1L);

        // When & Then: otherUserId가 환불 시도
        assertThatThrownBy(() ->
                refundService.processRefund(otherUserId, payment.getId(), items, "환불"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
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
        inventoryService.initializeInventory(saved.getId());
        return saved;
    }

    private List<RefundItemRequest> createRefundItemRequests(Long orderItemId, Long quantity) {
        return List.of(createRefundItemRequest(orderItemId, quantity));
    }

    private RefundItemRequest createRefundItemRequest(Long orderItemId, Long quantity) {
        // RefundItemRequest는 Getter만 있고 Setter가 없으므로 리플렉션 사용
        try {
            RefundItemRequest request = new RefundItemRequest();
            java.lang.reflect.Field orderItemIdField = RefundItemRequest.class.getDeclaredField("orderItemId");
            orderItemIdField.setAccessible(true);
            orderItemIdField.set(request, orderItemId);

            java.lang.reflect.Field quantityField = RefundItemRequest.class.getDeclaredField("quantity");
            quantityField.setAccessible(true);
            quantityField.set(request, quantity);

            return request;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create RefundItemRequest", e);
        }
    }
}
