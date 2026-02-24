package com.minishop.project.minishop.order.repository;

import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o WHERE o.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    List<Order> findByUserId(Long userId);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);



    /**
     * 주문 만료 스케줄러용 ID 전용 조회
     * 엔티티를 로드하지 않아 L1 캐시 오염을 방지한다.
     */
    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.createdAt < :createdAt")
    List<Long> findIdsByStatusAndCreatedAtBefore(@Param("status") OrderStatus status,
                                                  @Param("createdAt") Instant createdAt);

    /**
     * OrderItems를 즉시 로딩하여 조회
     * 비동기 이벤트 처리에서 사용 (Lazy Loading 방지)
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);
}
