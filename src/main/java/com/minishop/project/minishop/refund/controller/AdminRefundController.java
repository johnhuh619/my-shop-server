package com.minishop.project.minishop.refund.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.refund.domain.Refund;
import com.minishop.project.minishop.refund.domain.RefundStatus;
import com.minishop.project.minishop.refund.dto.ApproveRefundRequest;
import com.minishop.project.minishop.refund.dto.RefundResponse;
import com.minishop.project.minishop.refund.dto.RejectRefundRequest;
import com.minishop.project.minishop.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private final RefundService refundService;

    @GetMapping
    public ApiResponse<List<RefundResponse>> getRefundRequests(
            @RequestParam(required = false) RefundStatus status) {
        RefundStatus queryStatus = (status != null) ? status : RefundStatus.REQUESTED;
        List<Refund> refunds = refundService.getRefundsByStatus(queryStatus);
        List<RefundResponse> responses = refunds.stream()
                .map(RefundResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{id}")
    public ApiResponse<RefundResponse> getRefundDetail(@PathVariable Long id) {
        Refund refund = refundService.getRefundById(id);
        return ApiResponse.success(RefundResponse.from(refund));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<RefundResponse> approveRefund(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveRefundRequest request) {
        String comment = (request != null) ? request.getComment() : null;
        Refund refund = refundService.approveRefund(id, comment);
        return ApiResponse.success(RefundResponse.from(refund));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<RefundResponse> rejectRefund(
            @PathVariable Long id,
            @RequestBody(required = false) RejectRefundRequest request) {
        String comment = (request != null) ? request.getComment() : null;
        Refund refund = refundService.rejectRefund(id, comment);
        return ApiResponse.success(RefundResponse.from(refund));
    }
}
