package com.minishop.project.minishop.auth.domain;

import lombok.Getter;

/**
 * Represents the authenticated user in the security context.
 * This is the standard principal object used in Authentication.
 *
 * Rules:
 * - Authentication.getPrincipal() returns AuthenticatedUser
 * - Contains userId only (no roles, no authorities)
 * - Immutable and stateless
 */
@Getter
public class AuthenticatedUser {
    private final Long userId;

    private AuthenticatedUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        this.userId = userId;
    }

    public static AuthenticatedUser of(Long userId) {
        return new AuthenticatedUser(userId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthenticatedUser that = (AuthenticatedUser) o;
        return userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }

    @Override
    public String toString() {
        return "AuthenticatedUser{userId=" + userId + "}";
    }
}
