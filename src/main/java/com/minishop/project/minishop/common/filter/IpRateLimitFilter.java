package com.minishop.project.minishop.common.filter;

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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class IpRateLimitFilter extends OncePerRequestFilter {

    private final LettuceBasedProxyManager<String> proxyManager;
    private final JsonMapper jsonMapper;

    private final BucketConfiguration loginConfig;
    private final BucketConfiguration registerConfig;

    public IpRateLimitFilter(
            LettuceBasedProxyManager<String> proxyManager,
            JsonMapper jsonMapper,
            @Value("${rate-limit.login.capacity:5}") int loginCapacity,
            @Value("${rate-limit.login.refill-tokens:5}") int loginRefillTokens,
            @Value("${rate-limit.login.refill-duration-seconds:60}") int loginRefillSeconds,
            @Value("${rate-limit.register.capacity:3}") int registerCapacity,
            @Value("${rate-limit.register.refill-tokens:3}") int registerRefillTokens,
            @Value("${rate-limit.register.refill-duration-seconds:600}") int registerRefillSeconds) {
        this.proxyManager = proxyManager;
        this.jsonMapper = jsonMapper;

        this.loginConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(loginCapacity)
                        .refillGreedy(loginRefillTokens, Duration.ofSeconds(loginRefillSeconds))
                        .build())
                .build();

        this.registerConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(registerCapacity)
                        .refillGreedy(registerRefillTokens, Duration.ofSeconds(registerRefillSeconds))
                        .build())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return !("POST".equalsIgnoreCase(method) &&
                ("/api/auth/login".equals(path) || "/api/users/register".equals(path)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = resolveClientIp(request);
        String key = "rate:ip:" + path + ":" + clientIp;

        BucketConfiguration config = "/api/auth/login".equals(path) ? loginConfig : registerConfig;

        BucketProxy bucket = proxyManager.builder().build(key, () -> config);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000 + 1;
            writeRateLimitResponse(response, retryAfterSeconds);
            return;
        }

        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
