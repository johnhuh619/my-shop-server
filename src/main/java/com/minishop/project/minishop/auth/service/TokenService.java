package com.minishop.project.minishop.auth.service;

import com.minishop.project.minishop.auth.domain.TokenPayload;
import com.minishop.project.minishop.common.exception.BusinessException;
import com.minishop.project.minishop.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class TokenService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey secretKey;
    private final long validityInMilliseconds;

    public TokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long validityInMilliseconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityInMilliseconds = validityInMilliseconds;
    }

    public String issueToken(Long userId, String role) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    public TokenPayload validateAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            String role = claims.get(ROLE_CLAIM, String.class);
            TokenPayload payload = TokenPayload.of(
                    Long.parseLong(claims.getSubject()),
                    role != null ? role : "CUSTOMER",
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant()
            );

            if (payload.isExpired()){
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return payload;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
