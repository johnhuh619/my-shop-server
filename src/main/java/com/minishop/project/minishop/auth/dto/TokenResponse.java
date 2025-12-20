package com.minishop.project.minishop.auth.dto;

import lombok.Getter;

import java.util.Date;

@Getter
public class TokenResponse {
    private final String accessToken;
    private final Long userId;
    private final Date issuedAt;
    private final Date expiresAt;

    private TokenResponse(String accessToken, Long userId, Date issuedAt, Date expiresAt) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public static TokenResponse of(String accessToken, Long userId, Date issuedAt, Date expiresAt) {
        return new TokenResponse(accessToken, userId, issuedAt, expiresAt);
    }
}
