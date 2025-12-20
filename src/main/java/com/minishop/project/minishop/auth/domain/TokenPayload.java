package com.minishop.project.minishop.auth.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Date;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenPayload {
    private Long userId;
    private Instant issuedAt;
    private Instant expiresAt;

    private TokenPayload(Long userId, Instant issuedAt, Instant expiresAt) {
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public static TokenPayload of(Long userId, Instant issuedAt, Instant expiresAt) {
        return new TokenPayload(
                userId,
                issuedAt,
                expiresAt
        );
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
