package com.minishop.project.minishop.auth.service;

import com.minishop.project.minishop.auth.domain.TokenPayload;
import com.minishop.project.minishop.auth.dto.LoginResponse;
import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import com.minishop.project.minishop.user.domain.User;
import com.minishop.project.minishop.user.domain.UserStatus;
import com.minishop.project.minishop.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.USER_INACTIVE);
        }

        String accessToken = tokenService.issueToken(user.getId());

        return LoginResponse.of(accessToken, user.getId(), user.getEmail(), user.getName());
    }

    public TokenPayload validateToken(String token) {
        return tokenService.validateAccessToken(token);
    }
}
