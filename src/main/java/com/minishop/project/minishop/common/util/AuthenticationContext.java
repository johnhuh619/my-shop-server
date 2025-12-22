package com.minishop.project.minishop.common.util;

import com.minishop.project.minishop.auth.domain.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for accessing the current authenticated user.
 *
 * Usage in Controllers and Services:
 * <pre>
 * Long userId = AuthenticationContext.getCurrentUserId();
 * </pre>
 *
 * Rules:
 * - Returns the userId of the currently authenticated user
 * - Throws IllegalStateException if no authentication is present
 * - Only works after successful JWT authentication
 */
public class AuthenticationContext {

    private AuthenticationContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Get the userId of the currently authenticated user.
     *
     * @return userId (never null)
     * @throws IllegalStateException if no authentication is present or principal is invalid
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser)) {
            throw new IllegalStateException("Invalid principal type: " + principal.getClass().getName());
        }

        return ((AuthenticatedUser) principal).getUserId();
    }

    /**
     * Get the AuthenticatedUser object from the security context.
     *
     * @return AuthenticatedUser (never null)
     * @throws IllegalStateException if no authentication is present or principal is invalid
     */
    public static AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser)) {
            throw new IllegalStateException("Invalid principal type: " + principal.getClass().getName());
        }

        return (AuthenticatedUser) principal;
    }

    /**
     * Check if a user is currently authenticated.
     *
     * @return true if authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser;
    }
}
