package com.minishop.project.minishop.payment.service;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.domain.PaymentStatus;
import com.minishop.project.minishop.payment.event.PaymentCompletedEvent;
import com.minishop.project.minishop.payment.event.PaymentFailedEvent;
import com.minishop.project.minishop.payment.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class PaymentConfirmHandler {

    private static final long CONFIRM_WAIT_TIMEOUT_MILLIS = 10000;
    private static final long CONFIRM_POLL_INTERVAL_MILLIS = 50;

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    enum ConfirmPreparationType {
        READY,
        IN_PROGRESS,
        COMPLETED
    }

    record ConfirmPreparation(ConfirmPreparationType type, Payment payment) {
    }

    @Transactional
    ConfirmPreparation prepareConfirm(Long userId, String paymentKey, String tossOrderId, Long amount) {
        Payment payment = paymentRepository.findByTossOrderId(tossOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return new ConfirmPreparation(ConfirmPreparationType.COMPLETED, payment);
        }

        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.validateAmount(amount);
            if (payment.getPaymentKey() != null && !payment.getPaymentKey().equals(paymentKey)) {
                throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT,
                        "Payment key already assigned with different value");
            }
            return new ConfirmPreparation(ConfirmPreparationType.IN_PROGRESS, payment);
        }

        if (payment.getStatus() != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Payment can only be confirmed when status is REQUESTED");
        }

        // REQUESTED -> PROCESSING transition must be protected by row lock.
        Payment lockedPayment = paymentRepository.findByTossOrderIdWithLock(tossOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!lockedPayment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (lockedPayment.getStatus() == PaymentStatus.COMPLETED) {
            return new ConfirmPreparation(ConfirmPreparationType.COMPLETED, lockedPayment);
        }
        if (lockedPayment.getStatus() == PaymentStatus.PROCESSING) {
            lockedPayment.validateAmount(amount);
            if (lockedPayment.getPaymentKey() != null && !lockedPayment.getPaymentKey().equals(paymentKey)) {
                throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT,
                        "Payment key already assigned with different value");
            }
            return new ConfirmPreparation(ConfirmPreparationType.IN_PROGRESS, lockedPayment);
        }
        if (lockedPayment.getStatus() != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Payment can only be confirmed when status is REQUESTED");
        }

        lockedPayment.validateAmount(amount);
        lockedPayment.startProcessing(paymentKey);
        paymentRepository.save(lockedPayment);

        return new ConfirmPreparation(ConfirmPreparationType.READY, lockedPayment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Payment finalizeConfirmSuccess(Long userId, String tossOrderId) {
        Payment payment = paymentRepository.findByTossOrderIdWithLock(tossOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return payment;
        }

        if (payment.getStatus() != PaymentStatus.PROCESSING && payment.getStatus() != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Payment can only be completed when status is REQUESTED or PROCESSING");
        }

        payment.markAsCompleted();
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publishEvent(PaymentCompletedEvent.from(saved));
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void finalizeConfirmFailure(Long userId, String tossOrderId) {
        Payment payment = paymentRepository.findByTossOrderIdWithLock(tossOrderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }

        if (payment.getStatus() != PaymentStatus.PROCESSING && payment.getStatus() != PaymentStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "Payment can only be failed when status is REQUESTED or PROCESSING");
        }

        payment.markAsFailed();
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publishEvent(PaymentFailedEvent.from(saved));
    }

    Payment waitForCompletion(Long userId, String tossOrderId) {
        long deadline = System.currentTimeMillis() + CONFIRM_WAIT_TIMEOUT_MILLIS;

        while (System.currentTimeMillis() < deadline) {
            // Avoid stale first-level cache in request-scoped persistence context.
            entityManager.clear();
            Payment payment = paymentRepository.findByTossOrderId(tossOrderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

            if (!payment.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
            }

            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                return payment;
            }
            if (payment.getStatus() == PaymentStatus.FAILED) {
                throw new BusinessException(ErrorCode.PG_CONFIRM_FAILED, "Payment confirmation failed");
            }

            try {
                Thread.sleep(CONFIRM_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.PG_CONFIRM_FAILED,
                        "Interrupted while waiting for payment confirmation");
            }
        }

        throw new BusinessException(ErrorCode.PG_CONFIRM_FAILED, "Payment confirmation timed out");
    }
}
