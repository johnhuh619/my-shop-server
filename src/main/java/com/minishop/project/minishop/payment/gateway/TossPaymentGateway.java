package com.minishop.project.minishop.payment.gateway;

import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.payment.dto.TossCancelResponse;
import com.minishop.project.minishop.payment.dto.TossConfirmResponse;
import com.minishop.project.minishop.payment.dto.TossErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class TossPaymentGateway implements PaymentGateway {

    private final RestClient restClient;

    public TossPaymentGateway(
            @Value("${toss.payments.secret-key}") String secretKey,
            @Value("${toss.payments.base-url}") String baseUrl) {
        String encodedKey = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(10000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + encodedKey)
                .build();
    }

    @Override
    public TossConfirmResponse confirmPayment(String paymentKey, String tossOrderId, Long amount) {
        log.info("Requesting Toss confirm: paymentKey={}, orderId={}, amount={}",
                paymentKey, tossOrderId, amount);

        Map<String, Object> requestBody = Map.of(
                "paymentKey", paymentKey,
                "orderId", tossOrderId,
                "amount", amount
        );

        try {
            TossConfirmResponse response = restClient.post()
                    .uri("/payments/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(TossConfirmResponse.class);

            log.info("Toss confirm success: paymentKey={}, status={}",
                    paymentKey, response != null ? response.getStatus() : "null");
            return response;

        } catch (org.springframework.web.client.RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("Toss confirm failed: paymentKey={}, status={}, body={}",
                    paymentKey, e.getStatusCode(), responseBody);

            if (responseBody.contains("ALREADY_PROCESSED_PAYMENT")) {
                log.info("Payment already processed (idempotent): paymentKey={}", paymentKey);
                TossConfirmResponse idempotentResponse = new TossConfirmResponse();
                return idempotentResponse;
            }

            throw new BusinessException(ErrorCode.PG_CONFIRM_FAILED, responseBody);
        }
    }

    @Override
    public TossCancelResponse cancelPayment(String paymentKey, String cancelReason, Long cancelAmount) {
        log.info("Requesting Toss cancel: paymentKey={}, reason={}, amount={}",
                paymentKey, cancelReason, cancelAmount);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("cancelReason", cancelReason);
        if (cancelAmount != null) {
            requestBody.put("cancelAmount", cancelAmount);
        }

        try {
            TossCancelResponse response = restClient.post()
                    .uri("/payments/{paymentKey}/cancel", paymentKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(TossCancelResponse.class);

            log.info("Toss cancel success: paymentKey={}, status={}",
                    paymentKey, response != null ? response.getStatus() : "null");
            return response;

        } catch (org.springframework.web.client.RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("Toss cancel failed: paymentKey={}, status={}, body={}",
                    paymentKey, e.getStatusCode(), responseBody);

            throw new BusinessException(ErrorCode.PG_REFUND_FAILED, responseBody);
        }
    }
}
