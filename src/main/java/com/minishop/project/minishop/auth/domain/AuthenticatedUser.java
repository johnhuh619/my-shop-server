package com.minishop.project.minishop.auth.domain;

import lombok.Getter;

import java.util.Objects;

/**
 * Represents the authenticated user in the security context.
 * This is the standard principal object used in Authentication.
 *
 * Rules:
 * - Authentication.getPrincipal() returns AuthenticatedUser
 * - Contains userId and role
 * - Immutable and stateless
 */
@Getter
public class AuthenticatedUser {
    private final Long userId;
    private final String role;

    private AuthenticatedUser(Long userId, String role) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        this.userId = userId;
        this.role = role != null ? role : "CUSTOMER";
    }

    public static AuthenticatedUser of(Long userId, String role) {
        return new AuthenticatedUser(userId, role);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthenticatedUser that = (AuthenticatedUser) o;
        return userId.equals(that.userId) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, role);
    }

    @Override
    public String toString() {
        return "AuthenticatedUser{userId=" + userId + ", role=" + role + "}";
    }
}
