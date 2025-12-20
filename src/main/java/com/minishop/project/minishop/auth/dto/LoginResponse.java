package com.minishop.project.minishop.auth.dto;

import lombok.Getter;

@Getter
public class LoginResponse {
    private final String accessToken;
    private final Long userId;
    private final String email;
    private final String name;

    private LoginResponse(String accessToken, Long userId, String email, String name) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.email = email;
        this.name = name;
    }

    public static LoginResponse of(String accessToken, Long userId, String email, String name) {
        return new LoginResponse(accessToken, userId, email, name);
    }
}
