package com.minishop.project.minishop.common.filter;

import com.minishop.project.minishop.auth.domain.AuthenticatedUser;
import com.minishop.project.minishop.auth.domain.TokenPayload;
import com.minishop.project.minishop.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            try {
                TokenPayload payload = tokenService.validateAccessToken(token);
                AuthenticatedUser authenticatedUser = AuthenticatedUser.of(payload.getUserId());
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // Invalid token - continue without authentication
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
