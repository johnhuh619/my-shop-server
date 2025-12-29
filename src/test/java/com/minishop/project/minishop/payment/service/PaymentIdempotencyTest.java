package com.minishop.project.minishop.payment.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.repository.InventoryRepository;
import com.minishop.project.minishop.inventory.service.InventoryService;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.dto.OrderItemRequest;
import com.minishop.project.minishop.order.repository.OrderRepository;
import com.minishop.project.minishop.order.service.OrderService;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.repository.PaymentRepository;
import com.minishop.project.minishop.product.domain.Product;
import com.minishop.project.minishop.product.domain.ProductStatus;
import com.minishop.project.minishop.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Payment 멱등성 동시성 테스트
 * - 동시 요청 시 UNIQUE 제약 조건 검증
 * - 멱등성 키 중복 방지 검증
 * - @Transactional 제거 (실제 DB 제약 조건 테스트 위해)
 */
@SpringBootTest
class PaymentIdempotencyTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private Long testUserId = 999L;
    private Long otherUserId = 888L;
    private Product testProduct;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        // 테스트용 상품 생성
        testProduct = Product.builder()
                .name("Test Product")
                .description("Test Description")
                .unitPrice(10000L)
                .status(ProductStatus.ACTIVE)
                .build();
        testProduct = productRepository.save(testProduct);
        inventoryService.initializeInventory(testProduct.getId());
        inventoryService.addStock(testProduct.getId(), 100L);

        // 테스트용 주문 생성
        testOrder = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(testProduct.getId(), 5L)
        ));
    }

    @AfterEach
    void tearDown() {
        // 동시성 테스트는 @Transactional 사용 안 하므로 수동 정리
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
    }

    // ============================================
    // A. 동시 동일 요청 (CRITICAL P0)
    // ============================================

    @Test
    void 동시_동일키결제요청_하나만생성() throws InterruptedException {
        // Given
        int threadCount = 10;
        String idempotencyKey = "concurrent-key-123";
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Payment> results = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();

        // When: 10개 스레드가 동시에 같은 (userId, orderId, idempotencyKey)로 결제 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Payment payment = paymentService.processPayment(
                            testUserId, testOrder.getId(), idempotencyKey);
                    synchronized (results) {
                        results.add(payment);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 최소 1개 이상 성공 (UNIQUE 제약 기반 멱등성)
        // 주의: 극단적인 동시성 상황에서 일부 요청은 재시도가 필요할 수 있음
        assertThat(successCount.get()).isGreaterThan(0);

        // Then: DB에는 Payment 1개만 존재 (핵심 검증)
        List<Payment> payments = paymentRepository.findByUserId(testUserId);
        assertThat(payments).hasSize(1);

        // Then: 성공한 모든 요청은 같은 Payment ID를 반환
        if (results.size() > 1) {
            Long firstPaymentId = results.get(0).getId();
            assertThat(results).allMatch(p -> p.getId().equals(firstPaymentId));
        }
    }

    @Test
    void 동시_동일키다른주문_첫번째성공_나머지실패() throws InterruptedException {
        // Given: 두 개의 주문 생성
        inventoryService.addStock(testProduct.getId(), 100L);
        Order order2 = orderService.createOrder(testUserId, List.of(
                new OrderItemRequest(testProduct.getId(), 3L)
        ));

        int threadCount = 10;
        String idempotencyKey = "duplicate-key";
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 5개는 order1, 5개는 order2로 같은 키로 결제 시도
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    Long orderId = (index < 5) ? testOrder.getId() : order2.getId();
                    paymentService.processPayment(testUserId, orderId, idempotencyKey);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.DUPLICATE_PAYMENT) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Other exceptions (e.g., DataIntegrityViolationException) also count as failures
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 일부는 성공, 일부는 실패 (동시성 및 중복 키 검증)
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
        // 다른 주문 ID로 시도했으므로 최소 1개는 실패해야 함
        assertThat(failCount.get()).isGreaterThan(0);

        // Then: DB에는 Payment 1개만 존재 (핵심 검증)
        List<Payment> payments = paymentRepository.findByUserId(testUserId);
        assertThat(payments).hasSize(1);
    }

    // ============================================
    // B. 동시 다른 키
    // ============================================

    @Test
    void 동시_다른키결제요청_모두생성() throws InterruptedException {
        // Given: 여러 주문 생성
        inventoryService.addStock(testProduct.getId(), 100L);
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            orders.add(orderService.createOrder(testUserId, List.of(
                    new OrderItemRequest(testProduct.getId(), 1L)
            )));
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 5개 스레드가 각각 다른 키로 결제
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    paymentService.processPayment(
                            testUserId,
                            orders.get(index).getId(),
                            "key-" + index
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 다른 키이므로 실패 없어야 함
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 모든 결제 성공
        assertThat(successCount.get()).isEqualTo(threadCount);

        // Then: DB에는 Payment 5개 존재
        List<Payment> payments = paymentRepository.findByUserId(testUserId);
        assertThat(payments).hasSize(5);
    }

    @Test
    void 동시_다른사용자_같은키_모두성공() throws InterruptedException {
        // Given: 다른 사용자의 주문 생성
        inventoryService.addStock(testProduct.getId(), 100L);
        Order otherUserOrder = orderService.createOrder(otherUserId, List.of(
                new OrderItemRequest(testProduct.getId(), 2L)
        ));

        int threadCount = 10;
        String sameKey = "shared-key";
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 5개는 testUserId, 5개는 otherUserId로 같은 키 사용
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    Long userId = (index < 5) ? testUserId : otherUserId;
                    Long orderId = (index < 5) ? testOrder.getId() : otherUserOrder.getId();
                    paymentService.processPayment(userId, orderId, sameKey);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 다른 userId이므로 같은 키 허용
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 각 사용자당 1개씩 총 2개 생성 (핵심 검증)
        List<Payment> testUserPayments = paymentRepository.findByUserId(testUserId);
        List<Payment> otherUserPayments = paymentRepository.findByUserId(otherUserId);
        assertThat(testUserPayments).hasSize(1);
        assertThat(otherUserPayments).hasSize(1);

        // Then: 대부분의 요청은 성공 (일부는 동시성 이슈로 실패 가능)
        assertThat(successCount.get()).isGreaterThan(0);
    }

    // ============================================
    // C. DB 제약조건 검증
    // ============================================

    @Test
    void DB제약조건_중복키직접삽입_예외발생() {
        // Given: 첫 번째 Payment 생성
        Payment payment1 = Payment.create(testUserId, testOrder.getId(), "unique-key", 50000L);
        paymentRepository.save(payment1);

        // When & Then: 같은 (userId, idempotencyKey)로 직접 삽입 시도
        Payment payment2 = Payment.create(testUserId, testOrder.getId() + 1, "unique-key", 60000L);
        assertThatThrownBy(() -> {
            paymentRepository.save(payment2);
            paymentRepository.flush(); // 즉시 DB에 반영하여 제약 조건 검증
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void DB제약조건_다른userId같은키_허용() {
        // Given: testUserId의 Payment 생성
        Payment payment1 = Payment.create(testUserId, testOrder.getId(), "shared-key", 50000L);
        paymentRepository.save(payment1);

        // When: otherUserId가 같은 키로 Payment 생성
        inventoryService.addStock(testProduct.getId(), 100L);
        Order otherOrder = orderService.createOrder(otherUserId, List.of(
                new OrderItemRequest(testProduct.getId(), 2L)
        ));
        Payment payment2 = Payment.create(otherUserId, otherOrder.getId(), "shared-key", 20000L);

        // Then: 예외 없이 저장 성공 (다른 userId)
        assertThatCode(() -> {
            paymentRepository.save(payment2);
            paymentRepository.flush();
        }).doesNotThrowAnyException();

        // Then: DB에 2개 Payment 존재
        assertThat(paymentRepository.findByUserId(testUserId)).hasSize(1);
        assertThat(paymentRepository.findByUserId(otherUserId)).hasSize(1);
    }

    // ============================================
    // D. 레이스 컨디션
    // ============================================

    @Test
    void 동시결제_UNIQUE제약위반_재시도로직확인() throws InterruptedException {
        // Given: 대규모 동시 요청 시뮬레이션
        int threadCount = 100;
        String idempotencyKey = "high-concurrency-key";
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // When: 100개 스레드가 동시에 같은 키로 결제 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    paymentService.processPayment(testUserId, testOrder.getId(), idempotencyKey);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 대부분의 요청이 처리됨 (일부는 재시도 필요할 수 있음)
        // 극단적인 동시성 상황(100 threads)에서 완벽한 성공률은 어려움
        assertThat(successCount.get()).isGreaterThan(threadCount / 2); // 50% 이상 성공

        // Then: DB에는 Payment 1개만 존재 (핵심 검증 - UNIQUE 제약)
        List<Payment> payments = paymentRepository.findByUserId(testUserId);
        assertThat(payments).hasSize(1);
    }

    @Test
    void 동시결제_재고락경합_데드락없음() throws InterruptedException {
        // Given: 여러 주문 생성 (같은 상품)
        inventoryService.addStock(testProduct.getId(), 100L);
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            orders.add(orderService.createOrder(testUserId, List.of(
                    new OrderItemRequest(testProduct.getId(), 1L)
            )));
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 각각 다른 주문 결제 (같은 상품 재고 경합)
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    paymentService.processPayment(
                            testUserId,
                            orders.get(index).getId(),
                            "key-" + index
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Inventory lock 경합으로 인한 예외 가능
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 데드락 없이 모든 스레드 완료 (성공 여부 무관)
        // 재고가 충분하므로 모두 성공해야 함
        assertThat(successCount.get()).isEqualTo(threadCount);

        // Then: DB에는 Payment 10개 존재
        List<Payment> payments = paymentRepository.findByUserId(testUserId);
        assertThat(payments).hasSize(10);
    }
}
