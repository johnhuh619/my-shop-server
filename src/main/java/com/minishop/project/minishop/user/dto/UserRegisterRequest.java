package com.minishop.project.minishop.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserRegisterRequest {
    private String email;
    private String name;

    public UserRegisterRequest(String email, String name) {
        this.email = email;
        this.name = name;
    }
}
