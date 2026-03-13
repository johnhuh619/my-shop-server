package com.minishop.project.minishop.common.filter;

import com.minishop.project.minishop.auth.domain.AuthenticatedUser;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.common.response.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class UserRateLimitFilter extends OncePerRequestFilter {

    private final LettuceBasedProxyManager<String> proxyManager;
    private final JsonMapper jsonMapper;
    private final BucketConfiguration paymentConfig;

    public UserRateLimitFilter(
            LettuceBasedProxyManager<String> proxyManager,
            JsonMapper jsonMapper,
            @Value("${rate-limit.payment.capacity:10}") int capacity,
            @Value("${rate-limit.payment.refill-tokens:10}") int refillTokens,
            @Value("${rate-limit.payment.refill-duration-seconds:60}") int refillSeconds) {
        this.proxyManager = proxyManager;
        this.jsonMapper = jsonMapper;

        this.paymentConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refillTokens, Duration.ofSeconds(refillSeconds))
                        .build())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/payments");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String key = "rate:user:payments:" + resolveKey(request);

        BucketProxy bucket = proxyManager.builder().build(key, () -> paymentConfig);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000 + 1;
            writeRateLimitResponse(response, retryAfterSeconds);
            return;
        }

        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
        filterChain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return String.valueOf(user.getUserId());
        }
        // Fallback to IP if not authenticated
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return "ip:" + xForwardedFor.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        ErrorCode errorCode = ErrorCode.RATE_LIMIT_EXCEEDED;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        ApiResponse<Void> body = ApiResponse.error(errorCode.getCode(), errorCode.getMessage());
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
