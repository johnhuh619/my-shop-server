package com.minishop.project.minishop.common.exception;

import com.minishop.project.minishop.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        HttpStatus status = resolveHttpStatus(errorCode);
        return ResponseEntity.status(status)
                .body(ApiResponse.error(errorCode.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("C002", message));
    }

    private HttpStatus resolveHttpStatus(ErrorCode code) {
        return switch (code) {
            case USER_NOT_FOUND, ORDER_NOT_FOUND, PAYMENT_NOT_FOUND,
                 PRODUCT_NOT_FOUND, INVENTORY_NOT_FOUND, REFUND_NOT_FOUND,
                 ORDER_ITEM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_CREDENTIALS, INVALID_TOKEN -> HttpStatus.UNAUTHORIZED;
            case DUPLICATE_EMAIL, DUPLICATE_PAYMENT, INSUFFICIENT_INVENTORY -> HttpStatus.CONFLICT;
            case INVALID_INPUT_VALUE, INVALID_ORDER_STATUS, INVALID_REFUND_AMOUNT,
                 INVALID_REFUND_STATUS, PAYMENT_AMOUNT_MISMATCH,
                 REFUND_NOT_ALLOWED, REFUND_AMOUNT_EXCEEDED, REFUND_QUANTITY_EXCEEDED,
                 ORDER_ALREADY_PAID, ORDER_EXPIRED -> HttpStatus.BAD_REQUEST;
            case PG_CONFIRM_FAILED, PG_REFUND_FAILED -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
