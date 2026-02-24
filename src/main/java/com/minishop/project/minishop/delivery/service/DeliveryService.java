package com.minishop.project.minishop.delivery.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.delivery.domain.Delivery;
import com.minishop.project.minishop.delivery.domain.DeliveryStatus;
import com.minishop.project.minishop.delivery.dto.CreateDeliveryRequest;
import com.minishop.project.minishop.delivery.dto.ShipDeliveryRequest;
import com.minishop.project.minishop.delivery.event.DeliveryCompletedEvent;
import com.minishop.project.minishop.delivery.repository.DeliveryRepository;
import com.minishop.project.minishop.order.domain.Order;
import com.minishop.project.minishop.order.domain.OrderStatus;
import com.minishop.project.minishop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Delivery createDelivery(CreateDeliveryRequest request) {
        // Idempotent: return existing delivery if already exists
        return deliveryRepository.findByOrderId(request.getOrderId())
                .orElseGet(() -> {
                    Order order = orderService.getOrderById(request.getOrderId());
                    if (order.getStatus() != OrderStatus.PAID) {
                        throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                                "Delivery can only be created for PAID orders");
                    }

                    Delivery delivery = Delivery.create(
                            order.getId(), order.getUserId(),
                            request.getRecipientName(), request.getRecipientPhone(),
                            request.getAddress(), request.getAddressDetail(),
                            request.getZipCode());

                    return deliveryRepository.save(delivery);
                });
    }

    @Transactional
    public Delivery shipDelivery(Long deliveryId, ShipDeliveryRequest request) {
        Delivery delivery = getDeliveryById(deliveryId);
        delivery.ship(request.getCarrier(), request.getTrackingNumber());
        return deliveryRepository.save(delivery);
    }

    @Transactional
    public Delivery markInTransit(Long deliveryId) {
        Delivery delivery = getDeliveryById(deliveryId);
        delivery.markInTransit();
        return deliveryRepository.save(delivery);
    }

    @Transactional
    public Delivery markDelivered(Long deliveryId) {
        Delivery delivery = getDeliveryById(deliveryId);
        delivery.markDelivered();
        Delivery saved = deliveryRepository.save(delivery);

        eventPublisher.publishEvent(new DeliveryCompletedEvent(saved.getId(), saved.getOrderId()));
        log.info("Published DeliveryCompletedEvent: deliveryId={}, orderId={}", saved.getId(), saved.getOrderId());

        return saved;
    }

    @Transactional
    public Delivery cancelDelivery(Long deliveryId) {
        Delivery delivery = getDeliveryById(deliveryId);
        delivery.cancel();
        return deliveryRepository.save(delivery);
    }

    @Transactional(readOnly = true)
    public Delivery getDeliveryById(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Delivery getDeliveryByOrderId(Long orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Delivery getDeliveryByIdAndUserId(Long deliveryId, Long userId) {
        return deliveryRepository.findByIdAndUserId(deliveryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Delivery getDeliveryByOrderIdAndUserId(Long orderId, Long userId) {
        return deliveryRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Delivery> getDeliveriesByUserId(Long userId) {
        return deliveryRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Delivery> getDeliveriesByStatus(DeliveryStatus status) {
        return deliveryRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Delivery> getAllDeliveries() {
        return deliveryRepository.findAll();
    }
}
