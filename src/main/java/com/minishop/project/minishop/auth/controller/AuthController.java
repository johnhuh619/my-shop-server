package com.minishop.project.minishop.auth.controller;

import com.minishop.project.minishop.auth.dto.LoginRequest;
import com.minishop.project.minishop.auth.dto.LoginResponse;
import com.minishop.project.minishop.auth.service.AuthService;
import com.minishop.project.minishop.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.getEmail(), request.getPassword());
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout() {
        // TODO: Implement logout logic later
        // Options to consider:
        // 1. Client-side only (stateless, simple)
        // 2. Token blacklist with Redis (immediate invalidation)
        // 3. Token version management in DB (per-user invalidation)
        return ApiResponse.success(null);
    }
}
