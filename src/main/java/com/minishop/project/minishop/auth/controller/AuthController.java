package com.minishop.project.minishop.auth.controller;

import com.minishop.project.minishop.auth.dto.LoginRequest;
import com.minishop.project.minishop.auth.dto.LoginResponse;
import com.minishop.project.minishop.auth.dto.LogoutRequest;
import com.minishop.project.minishop.auth.dto.RefreshTokenRequest;
import com.minishop.project.minishop.auth.dto.RefreshTokenResponse;
import com.minishop.project.minishop.auth.service.AuthService;
import com.minishop.project.minishop.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.getEmail(), request.getPassword());
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(HttpServletRequest request,
                                  @RequestBody(required = false) LogoutRequest logoutRequest) {
        String accessToken = extractToken(request);
        String refreshToken = logoutRequest != null ? logoutRequest.getRefreshToken() : null;
        authService.logout(accessToken, refreshToken);
        return ApiResponse.success(null);
    }

    @PostMapping("/refresh")
    public ApiResponse<RefreshTokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        String newAccessToken = authService.refreshAccessToken(request.getRefreshToken());
        return ApiResponse.success(RefreshTokenResponse.of(newAccessToken));
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
