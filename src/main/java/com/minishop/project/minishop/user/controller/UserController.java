package com.minishop.project.minishop.user.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.common.util.AuthenticationContext;
import com.minishop.project.minishop.user.domain.User;
import com.minishop.project.minishop.user.dto.UserRegisterRequest;
import com.minishop.project.minishop.user.dto.UserResponse;
import com.minishop.project.minishop.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<UserResponse> registerUser(@RequestBody UserRegisterRequest request) {
        User user = userService.registerUser(
                request.getEmail(),
                request.getPassword(),
                request.getName()
        );
        return ApiResponse.success(UserResponse.from(user));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser() {
        Long id = AuthenticationContext.getCurrentUserId();
        User user = userService.getUserById(id);
        return ApiResponse.success(UserResponse.from(user));
    }

    @PatchMapping("/me/deactivate")
    public ApiResponse<UserResponse> deactivateUser() {
        Long id = AuthenticationContext.getCurrentUserId();
        User user = userService.deactivateUser(id);
        return ApiResponse.success(UserResponse.from(user));
    }
}
