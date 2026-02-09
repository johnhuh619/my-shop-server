package com.minishop.project.minishop.auth.dto;

import lombok.Getter;

@Getter
public class RefreshTokenResponse {
    private final String accessToken;

    private RefreshTokenResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public static RefreshTokenResponse of(String accessToken) {
        return new RefreshTokenResponse(accessToken);
    }
}
