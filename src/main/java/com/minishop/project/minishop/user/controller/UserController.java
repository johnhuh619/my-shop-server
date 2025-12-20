package com.minishop.project.minishop.user.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.user.dto.UserRegisterRequest;
import com.minishop.project.minishop.user.dto.UserResponse;
import com.minishop.project.minishop.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<UserResponse> registerUser(@RequestBody UserRegisterRequest request) {
        // TODO: Implement registration logic
        return null;
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUserById(@PathVariable Long id) {
        // TODO: Implement get user by id logic
        return null;
    }

    @GetMapping("/email/{email}")
    public ApiResponse<UserResponse> getUserByEmail(@PathVariable String email) {
        // TODO: Implement get user by email logic
        return null;
    }

    @PatchMapping("/{id}/deactivate")
    public ApiResponse<UserResponse> deactivateUser(@PathVariable Long id) {
        // TODO: Implement deactivate user logic
        return null;
    }
}
