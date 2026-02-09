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
import com.minishop.project.minishop.auth.service.TokenBlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * PaymentService 통합 테스트
 * - preparePayment + confirmPayment 2단계 흐름
 * - 멱등성, 소유권, 스냅샷, 실패 보상 검증
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentServiceTest {

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

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
        testGateway.reset();
    }

    // ============================================
    // 기본 결제 준비 테스트
    // ============================================

    @Test
    void preparePayment_정상준비_REQUESTED상태반환() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));

        // When
        Payment payment = paymentService.preparePayment(testUserId, order.getId(), "key-123");

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getUserId()).isEqualTo(testUserId);
        assertThat(payment.getOrderId()).isEqualTo(order.getId());
        assertThat(payment.getAmount()).isEqualTo(20000L);
        assertThat(payment.getTossOrderId()).startsWith("MINISHOP_");
        assertThat(payment.getPaymentKey()).isNull();
    }

    @Test
    void preparePayment_tossOrderId_형식검증() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));

        // When
        Payment payment = paymentService.preparePayment(testUserId, order.getId(), "key");

        // Then: MINISHOP_{orderId}_{uuid8} 형식
        assertThat(payment.getTossOrderId()).matches("MINISHOP_\\d+_[a-f0-9]{8}");
    }

    @Test
    void preparePayment_주문없음_예외발생() {
        assertThatThrownBy(() ->
                paymentService.preparePayment(testUserId, 9999L, "key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void preparePayment_CANCELED주문_예외발생() {
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        orderService.cancelOrder(order.getId(), testUserId);

        assertThatThrownBy(() ->
                paymentService.preparePayment(testUserId, order.getId(), "key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void preparePayment_EXPIRED주문_예외발생() {
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        orderService.expireOrder(order.getId());

        assertThatThrownBy(() ->
                paymentService.preparePayment(testUserId, order.getId(), "key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_STATUS);
    }

    // ============================================
    // 결제 승인(confirm) 테스트
    // ============================================

    @Test
    void confirmPayment_정상승인_COMPLETED상태() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));
        Payment prepared = paymentService.preparePayment(testUserId, order.getId(), "key-123");

        // When
        Payment confirmed = paymentService.confirmPayment(
                testUserId, "test-payment-key", prepared.getTossOrderId(), 20000L
        );

        // Then
        assertThat(confirmed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(confirmed.getPaymentKey()).isEqualTo("test-payment-key");
    }

    @Test
    void confirmPayment_Order_PAID_이벤트기반전이확인() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));
        Payment prepared = paymentService.preparePayment(testUserId, order.getId(), "key-123");

        // When
        paymentService.confirmPayment(
                testUserId, "test-payment-key", prepared.getTossOrderId(), 20000L
        );

        // Then: 비동기 이벤트로 Order PAID 전이
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Order paidOrder = orderService.getOrder(order.getId(), testUserId);
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        });
    }

    @Test
    void confirmPayment_금액불일치_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));
        Payment prepared = paymentService.preparePayment(testUserId, order.getId(), "key");

        // When & Then: 위변조된 금액
        assertThatThrownBy(() ->
                paymentService.confirmPayment(testUserId, "pk", prepared.getTossOrderId(), 99999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    @Test
    void confirmPayment_타인결제_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        Payment prepared = paymentService.preparePayment(testUserId, order.getId(), "key");

        // When & Then
        assertThatThrownBy(() ->
                paymentService.confirmPayment(otherUserId, "pk", prepared.getTossOrderId(), 10000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void confirmPayment_이미완료_멱등성반환() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        Payment prepared = paymentService.preparePayment(testUserId, order.getId(), "key");

        // 첫 번째 confirm
        Payment first = paymentService.confirmPayment(
                testUserId, "pk", prepared.getTossOrderId(), 10000L
        );
        assertThat(first.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        // When: 두 번째 confirm (멱등성)
        Payment second = paymentService.confirmPayment(
                testUserId, "pk", prepared.getTossOrderId(), 10000L
        );

        // Then: 같은 Payment 반환
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void confirmPayment_잘못된tossOrderId_예외발생() {
        assertThatThrownBy(() ->
                paymentService.confirmPayment(testUserId, "pk", "INVALID_ORDER_ID", 10000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void confirmPayment_PG실패_FAILED상태_이벤트기반재고해제() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));
        Payment prepared = paymentService.preparePayment(testUserId, order.getId(), "key");
        testGateway.setShouldFail(true);

        // When & Then: confirm 실패
        assertThatThrownBy(() ->
                paymentService.confirmPayment(
                        testUserId, "pk", prepared.getTossOrderId(), 50000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PG_CONFIRM_FAILED);

        // Then: Payment FAILED
        Payment failed = paymentService.getPayment(prepared.getId(), testUserId);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);

        // Then: 비동기 이벤트로 재고 해제
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Inventory inventory = inventoryService.getByProductId(product.getId());
            assertThat(inventory.getQuantityAvailable()).isEqualTo(10L);
            assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
        });
    }

    // ============================================
    // 멱등성 테스트 (prepare 단계)
    // ============================================

    @Test
    void preparePayment_동일키재요청_기존결제반환() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));

        Payment first = paymentService.preparePayment(testUserId, order.getId(), "idempotent-key");
        Payment second = paymentService.preparePayment(testUserId, order.getId(), "idempotent-key");

        // Then
        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        List<Payment> payments = paymentRepository.findByUserId(testUserId);
        assertThat(payments).hasSize(1);
    }

    @Test
    void preparePayment_동일키다른주문_예외발생() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 20L);
        Order order1 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        Order order2 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));

        paymentService.preparePayment(testUserId, order1.getId(), "same-key");

        assertThatThrownBy(() ->
                paymentService.preparePayment(testUserId, order2.getId(), "same-key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_PAYMENT);
    }

    @Test
    void preparePayment_다른키요청_새결제생성() {
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 20L);
        Order order1 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));
        Order order2 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));

        Payment payment1 = paymentService.preparePayment(testUserId, order1.getId(), "key-1");
        Payment payment2 = paymentService.preparePayment(testUserId, order2.getId(), "key-2");

        assertThat(payment1.getId()).isNotEqualTo(payment2.getId());
    }

    // ============================================
    // 결제 실패 보상 테스트
    // ============================================

    @Test
    void confirmPayment_여러상품결제실패_모든재고해제() {
        // Given
        Product product1 = createProduct("Product A", 1000L);
        Product product2 = createProduct("Product B", 2000L);
        inventoryService.addStock(product1.getId(), 100L);
        inventoryService.addStock(product2.getId(), 50L);

        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product1.getId(), 3L),
                new OrderItemRequest(product2.getId(), 2L)
        ));
        Long expectedAmount = 1000L * 3L + 2000L * 2L; // 7000

        Payment prepared = paymentService.preparePayment(testUserId, order.getId(), "key");
        testGateway.setShouldFail(true);

        // When
        assertThatThrownBy(() ->
                paymentService.confirmPayment(
                        testUserId, "pk", prepared.getTossOrderId(), expectedAmount))
                .isInstanceOf(BusinessException.class);

        // Then: 모든 상품 재고 해제
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Inventory inv1 = inventoryService.getByProductId(product1.getId());
            assertThat(inv1.getQuantityAvailable()).isEqualTo(100L);
            assertThat(inv1.getQuantityReserved()).isEqualTo(0L);

            Inventory inv2 = inventoryService.getByProductId(product2.getId());
            assertThat(inv2.getQuantityAvailable()).isEqualTo(50L);
            assertThat(inv2.getQuantityReserved()).isEqualTo(0L);
        });
    }

    // ============================================
    // 소유권 테스트
    // ============================================

    @Test
    void getPayment_본인결제조회_성공() {
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));

        Payment payment = paymentService.preparePayment(testUserId, order.getId(), "key");
        Payment retrieved = paymentService.getPayment(payment.getId(), testUserId);

        assertThat(retrieved.getId()).isEqualTo(payment.getId());
        assertThat(retrieved.getUserId()).isEqualTo(testUserId);
    }

    @Test
    void getPayment_타인결제조회_예외발생() {
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));

        Payment payment = paymentService.preparePayment(testUserId, order.getId(), "key");

        assertThatThrownBy(() ->
                paymentService.getPayment(payment.getId(), otherUserId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void preparePayment_타인주문결제_예외발생() {
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)
        ));

        assertThatThrownBy(() ->
                paymentService.preparePayment(otherUserId, order.getId(), "key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
    }

    // ============================================
    // 스냅샷 테스트
    // ============================================

    @Test
    void preparePayment_주문금액스냅샷_저장확인() {
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 5L)
        ));

        Payment payment = paymentService.preparePayment(testUserId, order.getId(), "key");

        assertThat(payment.getAmount()).isEqualTo(50000L);
        assertThat(payment.getAmount()).isEqualTo(order.getTotalAmount());
    }

    @Test
    void preparePayment_스냅샷금액_Order와동일() {
        Product product1 = createProduct("Product A", 1500L);
        Product product2 = createProduct("Product B", 2500L);
        inventoryService.addStock(product1.getId(), 10L);
        inventoryService.addStock(product2.getId(), 10L);

        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product1.getId(), 3L),
                new OrderItemRequest(product2.getId(), 2L)
        ));

        Payment payment = paymentService.preparePayment(testUserId, order.getId(), "key");

        Long expectedAmount = 1500L * 3L + 2500L * 2L;
        assertThat(payment.getAmount()).isEqualTo(expectedAmount);
        assertThat(payment.getAmount()).isEqualTo(order.getTotalAmount());
    }

    // ============================================
    // 사용자 결제 내역 테스트
    // ============================================

    @Test
    void getPaymentsByUser_본인결제만조회() {
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 100L);

        Order order1 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 1L)));
        Order order2 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)));
        Order order3 = orderService.createOrder(otherUserId, List.of(
                new OrderItemRequest(product.getId(), 3L)));

        paymentService.preparePayment(testUserId, order1.getId(), "key-1");
        paymentService.preparePayment(testUserId, order2.getId(), "key-2");
        paymentService.preparePayment(otherUserId, order3.getId(), "key-3");

        List<Payment> payments = paymentService.getPaymentsByUser(testUserId);

        assertThat(payments).hasSize(2);
        assertThat(payments).allMatch(p -> p.getUserId().equals(testUserId));
    }

    @Test
    void getPaymentsByUser_결제없음_빈리스트() {
        List<Payment> payments = paymentService.getPaymentsByUser(999999L);
        assertThat(payments).isEmpty();
    }

    // ============================================
    // E2E 흐름 테스트
    // ============================================

    @Test
    void 전체흐름_prepare후confirm_성공_이벤트처리완료() {
        // Given
        Product product = createProduct("Test Product", 10000L);
        inventoryService.addStock(product.getId(), 10L);
        Order order = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(product.getId(), 2L)
        ));

        // Step 1: prepare
        Payment prepared = paymentService.preparePayment(testUserId, order.getId(), "e2e-key");
        assertThat(prepared.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(prepared.getTossOrderId()).isNotNull();
        assertThat(prepared.getPaymentKey()).isNull();

        // Step 2: confirm
        Payment confirmed = paymentService.confirmPayment(
                testUserId, "toss-pk-123", prepared.getTossOrderId(), 20000L
        );
        assertThat(confirmed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(confirmed.getPaymentKey()).isEqualTo("toss-pk-123");

        // Step 3: 비동기 이벤트 - Order PAID
        await().atMost(5, SECONDS).untilAsserted(() -> {
            Order paidOrder = orderService.getOrder(order.getId(), testUserId);
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        });
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
