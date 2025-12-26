package com.minishop.project.minishop.inventory.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.inventory.domain.Inventory;
import com.minishop.project.minishop.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inventory 동시성 테스트
 * - PESSIMISTIC_WRITE 락 동작 검증
 * - 초과 판매 방지 검증
 * - @Transactional 제거 (실제 DB 락 테스트 위해)
 */
@SpringBootTest
class InventoryConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    private Long testProductId;

    @BeforeEach
    void setUp() {
        testProductId = 999L;
        Inventory inventory = Inventory.create(testProductId, 10L);
        inventoryRepository.save(inventory);
    }

    @AfterEach
    void tearDown() {
        // 동시성 테스트는 @Transactional 사용 안 하므로 수동 정리
        inventoryRepository.deleteAll();
    }

    @Test
    void 동시_재고예약_하나만_성공() throws InterruptedException {
        // Given: 재고 10개
        int threadCount = 10;
        int reserveQuantity = 6; // 10개 중 6개씩 예약 시도
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 6개씩 예약 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    inventoryService.reserve(testProductId, (long) reserveQuantity);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.INSUFFICIENT_INVENTORY) {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 하나만 성공, 나머지 9개는 재고 부족으로 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);

        // Then: 최종 재고 확인 (available=4, reserved=6)
        Inventory inventory = inventoryService.getByProductId(testProductId);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(4L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(6L);
    }

    @Test
    void 동시_재고예약_순차처리_확인() throws InterruptedException {
        // Given: 재고 100개
        Long productId = 888L;
        inventoryRepository.save(Inventory.create(productId, 100L));

        int threadCount = 20;
        int reserveQuantity = 3; // 각 스레드가 3개씩 예약
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 20개 스레드가 동시에 3개씩 예약 (총 60개 예약)
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    inventoryService.reserve(productId, (long) reserveQuantity);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 재고 충분하므로 실패 없어야 함
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 모든 예약 성공
        assertThat(successCount.get()).isEqualTo(20);

        // Then: 최종 재고 확인 (available=40, reserved=60)
        Inventory inventory = inventoryService.getByProductId(productId);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(40L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(60L);
        assertThat(inventory.getTotalQuantity()).isEqualTo(100L);

        // Cleanup
        inventoryRepository.deleteById(inventory.getId());
    }

    @Test
    void 동시_재고예약_경계값테스트() throws InterruptedException {
        // Given: 재고 10개
        int threadCount = 5;
        int reserveQuantity = 2; // 각 스레드가 2개씩 예약 (총 10개)
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 5개 스레드가 동시에 2개씩 예약
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    inventoryService.reserve(testProductId, (long) reserveQuantity);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패 시 무시
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 모든 예약 성공 (정확히 10개 소진)
        assertThat(successCount.get()).isEqualTo(5);

        // Then: 최종 재고 확인 (available=0, reserved=10)
        Inventory inventory = inventoryService.getByProductId(testProductId);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(0L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(10L);
    }

    @Test
    void 동시_재고해제_정합성확인() throws InterruptedException {
        // Given: 재고 예약 (available=0, reserved=10)
        inventoryService.reserve(testProductId, 10L);

        int threadCount = 5;
        int releaseQuantity = 2; // 각 스레드가 2개씩 해제
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 5개 스레드가 동시에 2개씩 해제
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    inventoryService.release(testProductId, (long) releaseQuantity);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패 시 무시
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 모든 해제 성공
        assertThat(successCount.get()).isEqualTo(5);

        // Then: 최종 재고 확인 (available=10, reserved=0)
        Inventory inventory = inventoryService.getByProductId(testProductId);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(10L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
    }

    @Test
    void 동시_재고확정_정합성확인() throws InterruptedException {
        // Given: 재고 예약 (available=0, reserved=10)
        inventoryService.reserve(testProductId, 10L);

        int threadCount = 5;
        int confirmQuantity = 2; // 각 스레드가 2개씩 확정
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 5개 스레드가 동시에 2개씩 확정
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    inventoryService.confirm(testProductId, (long) confirmQuantity);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패 시 무시
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 모든 확정 성공
        assertThat(successCount.get()).isEqualTo(5);

        // Then: 최종 재고 확인 (available=0, reserved=0, total=0)
        Inventory inventory = inventoryService.getByProductId(testProductId);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(0L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
        assertThat(inventory.getTotalQuantity()).isEqualTo(0L);
    }

    @Test
    void 동시_재고추가_정합성확인() throws InterruptedException {
        // Given: 초기 재고 10개
        int threadCount = 10;
        int addQuantity = 5; // 각 스레드가 5개씩 추가
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: 10개 스레드가 동시에 5개씩 추가
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    inventoryService.addStock(testProductId, (long) addQuantity);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 실패 시 무시
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then: 모든 추가 성공
        assertThat(successCount.get()).isEqualTo(10);

        // Then: 최종 재고 확인 (available=60, reserved=0)
        Inventory inventory = inventoryService.getByProductId(testProductId);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(60L);
        assertThat(inventory.getQuantityReserved()).isEqualTo(0L);
    }
}
