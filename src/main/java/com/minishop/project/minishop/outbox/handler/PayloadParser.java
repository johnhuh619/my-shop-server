package com.minishop.project.minishop.outbox.handler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple JSON payload parser for retry task handlers.
 * Handles payloads like {"orderId":123} without requiring ObjectMapper.
 */
public final class PayloadParser {

    private PayloadParser() {
    }

    public static Long extractLong(String payload, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Key '" + key + "' not found in payload: " + payload);
        }
        return Long.parseLong(matcher.group(1));
    }
}
