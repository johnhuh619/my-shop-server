package com.minishop.project.minishop.payment.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.domain.Inventory;
import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.dto.OrderItemRequest;
import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.domain.PaymentStatus;
import com.minishop.project.minishop.payment.gateway.PaymentGateway;
import com.minishop.project.minishop.payment.repository.PaymentRepository;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.domain.ProductStatus;
import com.minishop.project.minishop.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * PaymentService 통합 테스트
 * - 트랜잭션 경계 검증
 * - 서비스 간 협력 검증
 * - 멱등성 검증
 * - 실패 보상 검증
 *
 * 주의: @Transactional 제거
 * - 비동기 이벤트 테스트를 위해 트랜잭션이 실제로 커밋되어야 함
 * - @DirtiesContext로 테스트 간 격리 보장
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public PaymentGateway testPaymentGateway() {
            return new TestPaymentGateway();
        }
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TestPaymentGateway testGateway;

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    private Long testUserId = 999L;
    private Long otherUserId = 888L;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 게이트웨이 상태 초기화 (성공 모드)
        testGateway.reset();
    }

    // ============================================
    // 기본 결제 처리 테스트
    // ============================================

    @Test
    void processPayment_정상결제_성공() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));

        // When
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key-123");

        // Then: Payment는 REQUESTED 상태로 즉시 반환
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getUserId()).isEqualTo(testUserId);
        assertThat(payment.getOrderId()).isEqualTo(order.getId());
        assertThat(payment.getAmount()).isEqualTo(20000L); // 10000 * 2

        // Then: 비동기 이벤트 처리 완료 대기 - Payment COMPLETED
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Payment updated = paymentService.getPayment(payment.getId(), testUserId);
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        });

        // Then: 비동기 이벤트 처리 완료 대기 - Order PAID
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Order paidOrder = orderService.getOrder(order.getId(), testUserId);
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        });
    }

    @Test
    void processPayment_주문없음_예외발생() {
        // When & Then
        assertThatThrownBy(() ->
                paymentService.processPayment(testUserId, 9999L, "key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void processPayment_PAID주문_예외발생() {
        // Given: 이미 결제된 주문
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));

        paymentService.processPayment(testUserId, order.getId(), "key-1");

        // 비동기 이벤트 처리 완료 대기 - Order PAID 전이 확인
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Order paidOrder = orderService.getOrder(order.getId(), testUserId);
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        });

        // When & Then: 같은 주문 재결제 시도
        assertThatThrownBy(() ->
                paymentService.processPayment(testUserId, order.getId(), "key-2"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS)
                .hasMessageContaining("Order must be in CREATED status");
    }

    @Test
    void processPayment_CANCELED주문_예외발생() {
        // Given: 취소된 주문
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        orderService.cancelOrder(order.getId(), testUserId);

        // When & Then
        assertThatThrownBy(() ->
                paymentService.processPayment(testUserId, order.getId(), "key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void processPayment_EXPIRED주문_예외발생() {
        // Given: 만료된 주문
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        orderService.expireOrder(order.getId());

        // When & Then
        assertThatThrownBy(() ->
                paymentService.processPayment(testUserId, order.getId(), "key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }

    // ============================================
    // 멱등성 테스트 (CRITICAL P0)
    // ============================================

    @Test
    void processPayment_동일키재요청_기존결제반환() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));

        // When: 첫 번째 결제
        Payment firstPayment = paymentService.processPayment(testUserId, order.getId(), "idempotent-key");

        // When: 동일 키로 재요청
        Payment secondPayment = paymentService.processPayment(testUserId, order.getId(), "idempotent-key");

        // Then: 같은 Payment 반환 (REQUESTED 상태)
        assertThat(firstPayment.getId()).isEqualTo(secondPayment.getId());
        assertThat(firstPayment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(secondPayment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        // Then: 비동기 처리 후 COMPLETED 확인
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Payment updated = paymentService.getPayment(firstPayment.getId(), testUserId);
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        });

        // Then: DB에는 Payment 1개만 존재
        List<Payment> payments = paymentRepository.findByUserId(testUserId);
        assertThat(payments).hasSize(1);
    }

    @Test
    void processPayment_동일키다른주문_예외발생() {
        // Given: 두 개의 주문
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 20L);
        Order order1 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        Order order2 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));

        // When: order1 결제
        paymentService.processPayment(testUserId, order1.getId(), "same-key");

        // When & Then: 같은 키로 order2 결제 시도
        assertThatThrownBy(() ->
                paymentService.processPayment(testUserId, order2.getId(), "same-key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_PAYMENT);
    }

    @Test
    void processPayment_다른키요청_새결제생성() {
        // Given: 두 개의 주문
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 20L);
        Order order1 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        Order order2 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));

        // When
        Payment payment1 = paymentService.processPayment(testUserId, order1.getId(), "key-1");
        Payment payment2 = paymentService.processPayment(testUserId, order2.getId(), "key-2");

        // Then: 각각 다른 Payment 생성
        assertThat(payment1.getId()).isNotEqualTo(payment2.getId());
        assertThat(payment1.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(payment2.getIdempotencyKey()).isEqualTo("key-2");
    }

    // ============================================
    // 결제 실패 보상 테스트 (CRITICAL P0)
    // ============================================

    @Test
    void processPayment_결제실패시_재고해제됨() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));

        // Given: 외부 결제 실패 시뮬레이션
        testGateway.setShouldFail(true);

        // When
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // Then: Payment는 REQUESTED 상태로 즉시 반환
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        // Then: 비동기 처리 후 FAILED 확인
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Payment updated = paymentService.getPayment(payment.getId(), testUserId);
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
        });

        // Then: 비동기 이벤트로 재고 해제 확인
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Inventory inventory = inventoryService.getByProductId(product.getId());
            assertThat(inventory.getQuantityAvailable()).isEqualTo(10L); // 원복
            assertThat(inventory.getQuantityReserved()).isEqualTo(0L); // 해제
        });
    }

    @Test
    void processPayment_결제실패후재고확인() {
        // Given: 초기 재고 20개
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 20L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 10L)
        ));

        // Given: 결제 실패 시뮬레이션
        testGateway.setShouldFail(true);

        // When: 결제 실패
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // Then: 비동기 이벤트로 재고 완전 원복 확인
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Inventory inventory = inventoryService.getByProductId(product.getId());
            assertThat(inventory.getQuantityAvailable()).isEqualTo(20L);
            assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
            assertThat(inventory.getTotalQuantity()).isEqualTo(20L);
        });
    }

    @Test
    void processPayment_여러상품결제실패_모든재고해제() {
        // Given: 여러 상품
        Product product1 = createProduct("Product A", 1000L);
        Product product2 = createProduct("Product B", 2000L);
        inventoryService.addStock(product1.getId(), 100L);
        inventoryService.addStock(product2.getId(), 50L);

        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product1.getId(), 3L),
                new OrderItemRequest(product2.getId(), 2L)
        ));

        // Given: 결제 실패 시뮬레이션
        testGateway.setShouldFail(true);

        // When
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // Then: 비동기 이벤트로 모든 상품 재고 해제 확인
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Inventory inventory1 = inventoryService.getByProductId(product1.getId());
            assertThat(inventory1.getQuantityAvailable()).isEqualTo(100L);
            assertThat(inventory1.getQuantityReserved()).isEqualTo(0L);

            Inventory inventory2 = inventoryService.getByProductId(product2.getId());
            assertThat(inventory2.getQuantityAvailable()).isEqualTo(50L);
            assertThat(inventory2.getQuantityReserved()).isEqualTo(0L);
        });
    }

    // ============================================
    // 소유권 테스트
    // ============================================

    @Test
    void getPayment_본인결제조회_성공() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));

        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // When
        Payment retrieved = paymentService.getPayment(payment.getId(), testUserId);

        // Then
        assertThat(retrieved.getId()).isEqualTo(payment.getId());
        assertThat(retrieved.getUserId()).isEqualTo(testUserId);
    }

    @Test
    void getPayment_타인결제조회_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));

        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // When & Then: 다른 사용자가 조회 시도
        assertThatThrownBy(() ->
                paymentService.getPayment(payment.getId(), otherUserId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void processPayment_타인주문결제_예외발생() {
        // Given: testUserId의 주문
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));

        // When & Then: otherUserId가 결제 시도
        assertThatThrownBy(() ->
                paymentService.processPayment(otherUserId, order.getId(), "key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
    }

    // ============================================
    // 스냅샷 테스트
    // ============================================

    @Test
    void processPayment_주문금액스냅샷_저장확인() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));

        // When
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // Then: Payment.amount = Order.totalAmount 스냅샷
        assertThat(payment.getAmount()).isEqualTo(50000L); // 10000 * 5
        assertThat(payment.getAmount()).isEqualTo(order.getTotalAmount());
    }

    @Test
    void processPayment_스냅샷금액_Order와동일() {
        // Given: 여러 상품
        Product product1 = createProduct("Product A", 1500L);
        Product product2 = createProduct("Product B", 2500L);
        inventoryService.addStock(product1.getId(), 10L);
        inventoryService.addStock(product2.getId(), 10L);

        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product1.getId(), 3L),
                new OrderItemRequest(product2.getId(), 2L)
        ));

        // When
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // Then
        Long expectedAmount = 1500L * 3L + 2500L * 2L; // 9500
        assertThat(payment.getAmount()).isEqualTo(expectedAmount);
        assertThat(payment.getAmount()).isEqualTo(order.getTotalAmount());
    }

    // ============================================
    // 사용자 결제 내역 테스트
    // ============================================

    @Test
    void getPaymentsByUser_본인결제만조회() {
        // Given: testUserId 2개, otherUserId 1개 결제
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 100L);

        Order order1 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)));
        Order order2 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)));
        Order order3 = orderService.createOrder(otherUserId, List.of(
                new OrderItemRequest(product.getId(), 3L)));

        paymentService.processPayment(testUserId, order1.getId(), "key-1");
        paymentService.processPayment(testUserId, order2.getId(), "key-2");
        paymentService.processPayment(otherUserId, order3.getId(), "key-3");

        // When
        List<Payment> payments = paymentService.getPaymentsByUser(testUserId);

        // Then: testUserId 결제 2개만 조회
        assertThat(payments).hasSize(2);
        assertThat(payments).allMatch(p -> p.getUserId().equals(testUserId));
    }

    @Test
    void getPaymentsByUser_결제없음_빈리스트() {
        // When
        List<Payment> payments = paymentService.getPaymentsByUser(999999L);

        // Then
        assertThat(payments).isEmpty();
    }

    // ============================================
    // 트랜잭션 검증 테스트
    // ============================================

    @Test
    void processPayment_성공시_Order상태_PAID전이() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));

        // When
        Payment payment = paymentService.processPayment(testUserId, order.getId(), "key");

        // Then: 비동기 이벤트로 Order 상태 PAID로 전이 확인
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Order paidOrder = orderService.getOrder(order.getId(), testUserId);
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        });
    }

    @Test
    void processPayment_실패시_Order상태_CREATED유지() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));

        // Given: 결제 실패 시뮬레이션
        testGateway.setShouldFail(true);

        // When: 결제 실패
        paymentService.processPayment(testUserId, order.getId(), "key");

        // Then: Order 상태는 CREATED 유지 (PAID로 전이 안 됨)
        Order unchangedOrder = orderService.getOrderById(order.getId());
        assertThat(unchangedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
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
}
