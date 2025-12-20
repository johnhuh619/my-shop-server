package com.minishop.project.minishop.user.controller;

import com.minishop.project.minishop.common.response.ApiResponse;
import com.minishop.project.minishop.user.dto.UserRegisterRequest;
import com.minishop.project.minishop.user.dto.UserResponse;
import com.minishop.project.minishop.user.service.UserService;
import lombok.RequiredArgsConstructor;
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
}
