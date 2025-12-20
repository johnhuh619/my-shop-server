package com.minishop.project.minishop.user.dto;

import com.minishop.project.minishop.user.domain.User;
import com.minishop.project.minishop.user.domain.UserStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserResponse {
    private final Long id;
    private final String email;
    private final String name;
    private final UserStatus status;
    private final LocalDateTime createdAt;

    private UserResponse(Long id, String email, String name, UserStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
