package com.minishop.project.minishop.payment.controller;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.common.util.AuthenticationContext;
import com.minishop.project.minishop.payment.domain.Payment;
import com.minishop.project.minishop.payment.dto.ConfirmPaymentRequest;
import com.minishop.project.minishop.payment.dto.CreatePaymentRequest;
import com.minishop.project.minishop.payment.dto.PaymentResponse;
import com.minishop.project.minishop.payment.dto.PreparePaymentResponse;
import com.minishop.project.minishop.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ApiResponse<PreparePaymentResponse> preparePayment(
            @RequestHeader(value = "X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "X-Idempotency-Key header is required");
        }

        Long userId = AuthenticationContext.getCurrentUserId();
        Payment payment = paymentService.preparePayment(userId, request.getOrderId(), idempotencyKey);
        String orderName = paymentService.buildOrderName(payment.getOrderId());
        return ApiResponse.success(PreparePaymentResponse.from(payment, orderName));
    }

    @PostMapping("/confirm")
    public ApiResponse<PaymentResponse> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequest request) {

        Long userId = AuthenticationContext.getCurrentUserId();
        Payment payment = paymentService.confirmPayment(
                userId, request.getPaymentKey(), request.getOrderId(), request.getAmount()
        );
        return ApiResponse.success(PaymentResponse.from(payment));
    }

    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> getPayment(@PathVariable Long id) {
        Long userId = AuthenticationContext.getCurrentUserId();
        Payment payment = paymentService.getPayment(id, userId);
        return ApiResponse.success(PaymentResponse.from(payment));
    }

    @GetMapping
    public ApiResponse<List<PaymentResponse>> getMyPayments() {
        Long userId = AuthenticationContext.getCurrentUserId();
        List<Payment> payments = paymentService.getPaymentsByUser(userId);
        List<PaymentResponse> responses = payments.stream()
                .map(PaymentResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
