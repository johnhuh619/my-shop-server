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
    private final TokenBlacklistService tokenBlacklistService;

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

        String accessToken = tokenService.issueToken(user.getId(), user.getRole().name());
        String refreshToken = tokenService.issueRefreshToken(user.getId(), user.getRole().name());

        return LoginResponse.of(accessToken, refreshToken, user.getId(), user.getEmail(), user.getName());
    }

    public void logout(String accessToken, String refreshToken) {
        TokenPayload accessPayload = tokenService.parseClaimsForLogout(accessToken);
        tokenBlacklistService.blacklist(accessPayload.getJti(), accessPayload.getExpiresAt());

        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                TokenPayload refreshPayload = tokenService.parseClaimsForLogout(refreshToken);
                tokenBlacklistService.blacklist(refreshPayload.getJti(), refreshPayload.getExpiresAt());
            } catch (Exception e) {
                // Refresh token is invalid or expired - ignore, access token is already blacklisted
            }
        }
    }

    public String refreshAccessToken(String refreshToken) {
        TokenPayload payload = tokenService.validateRefreshToken(refreshToken);

        if (payload.getJti() != null && tokenBlacklistService.isBlacklisted(payload.getJti())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        return tokenService.issueToken(payload.getUserId(), payload.getRole());
    }
}
