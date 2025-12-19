package com.minishop.project.minishop.common.exception;

public enum ErrorCode {
    // Common
    INTERNAL_SERVER_ERROR("C001", "Internal server error"),
    INVALID_INPUT_VALUE("C002", "Invalid input value"),

    // User
    USER_NOT_FOUND("U001", "User not found"),

    // Product
    PRODUCT_NOT_FOUND("P001", "Product not found"),

    // Inventory
    INSUFFICIENT_INVENTORY("I001", "Insufficient inventory"),
    INVENTORY_NOT_FOUND("I002", "Inventory not found"),

    // Order
    ORDER_NOT_FOUND("O001", "Order not found"),
    INVALID_ORDER_STATUS("O002", "Invalid order status"),

    // Payment
    PAYMENT_NOT_FOUND("PAY001", "Payment not found"),
    DUPLICATE_PAYMENT("PAY002", "Duplicate payment"),
    PAYMENT_FAILED("PAY003", "Payment failed"),

    // Refund
    REFUND_NOT_FOUND("R001", "Refund not found"),
    INVALID_REFUND_AMOUNT("R002", "Invalid refund amount");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
