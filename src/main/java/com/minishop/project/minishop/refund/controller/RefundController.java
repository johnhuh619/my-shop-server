package com.minishop.project.minishop.refund.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.common.util.AuthenticationContext;
import com.minishop.project.minishop.refund.domain.Refund;
import com.minishop.project.minishop.refund.dto.CreateRefundRequest;
import com.minishop.project.minishop.refund.dto.RefundResponse;
import com.minishop.project.minishop.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    public ApiResponse<RefundResponse> processRefund(@RequestBody CreateRefundRequest request) {
        Long userId = AuthenticationContext.getCurrentUserId();
        Refund refund = refundService.processRefund(
                userId,
                request.getPaymentId(),
                request.getItems(),
                request.getReason()
        );
        return ApiResponse.success(RefundResponse.from(refund));
    }

    @GetMapping("/{id}")
    public ApiResponse<RefundResponse> getRefund(@PathVariable Long id) {
        Long userId = AuthenticationContext.getCurrentUserId();
        Refund refund = refundService.getRefund(id, userId);
        return ApiResponse.success(RefundResponse.from(refund));
    }

    @GetMapping
    public ApiResponse<List<RefundResponse>> getMyRefunds() {
        Long userId = AuthenticationContext.getCurrentUserId();
        List<Refund> refunds = refundService.getRefundsByUser(userId);
        List<RefundResponse> responses = refunds.stream()
                .map(RefundResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
