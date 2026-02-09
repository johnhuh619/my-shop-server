package com.minishop.project.minishop.auth.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenPayload {
    private Long userId;
    private String role;
    private Instant issuedAt;
    private Instant expiresAt;
    private String jti;

    private TokenPayload(Long userId, String role, Instant issuedAt, Instant expiresAt, String jti) {
        this.userId = userId;
        this.role = role;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.jti = jti;
    }

    public static TokenPayload of(Long userId, String role, Instant issuedAt, Instant expiresAt, String jti) {
        return new TokenPayload(userId, role, issuedAt, expiresAt, jti);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
