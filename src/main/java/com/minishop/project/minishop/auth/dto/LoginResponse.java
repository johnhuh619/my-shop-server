package com.minishop.project.minishop.auth.dto;

import lombok.Getter;

@Getter
public class LoginResponse {
    private final String accessToken;
    private final String refreshToken;
    private final Long userId;
    private final String email;
    private final String name;

    private LoginResponse(String accessToken, String refreshToken, Long userId, String email, String name) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.email = email;
        this.name = name;
    }

    public static LoginResponse of(String accessToken, String refreshToken, Long userId, String email, String name) {
        return new LoginResponse(accessToken, refreshToken, userId, email, name);
    }
}
