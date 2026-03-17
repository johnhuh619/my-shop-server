package com.minishop.project.minishop.common.filter;

import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalJobAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String INTERNAL_JOB_HEADER = "X-Internal-Job-Key";

    private final JsonMapper jsonMapper;
    private final String expectedAuthKey;

    public InternalJobAuthFilter(
            JsonMapper jsonMapper,
            @Value("${internal.jobs.auth-key}") String expectedAuthKey) {
        this.jsonMapper = jsonMapper;
        this.expectedAuthKey = expectedAuthKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String providedAuthKey = request.getHeader(INTERNAL_JOB_HEADER);
        if (!isAuthorized(providedAuthKey)) {
            writeUnauthorizedResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthorized(String providedAuthKey) {
        if (expectedAuthKey == null || expectedAuthKey.isBlank()) {
            return false;
        }
        if (providedAuthKey == null || providedAuthKey.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                expectedAuthKey.getBytes(StandardCharsets.UTF_8),
                providedAuthKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.UNAUTHORIZED_REQUEST.getCode(),
                ErrorCode.UNAUTHORIZED_REQUEST.getMessage()
        );
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
