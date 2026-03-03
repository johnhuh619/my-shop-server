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
import java.util.UUID;

@Service
public class TokenService {

    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final long validityInMilliseconds;
    private final long refreshValidityInMilliseconds;

    public TokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long validityInMilliseconds,
            @Value("${jwt.refresh-expiration}") long refreshValidityInMilliseconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityInMilliseconds = validityInMilliseconds;
        this.refreshValidityInMilliseconds = refreshValidityInMilliseconds;
    }

    public String issueToken(Long userId, String role) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role)
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    public String issueRefreshToken(Long userId, String role) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + refreshValidityInMilliseconds);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role)
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    public TokenPayload validateAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);

            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (tokenType != null && !TOKEN_TYPE_ACCESS.equals(tokenType)) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }

            String role = claims.get(ROLE_CLAIM, String.class);
            TokenPayload payload = TokenPayload.of(
                    Long.parseLong(claims.getSubject()),
                    role != null ? role : "CUSTOMER",
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant(),
                    claims.getId()
            );

            if (payload.isExpired()){
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return payload;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public TokenPayload validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);

            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }

            String role = claims.get(ROLE_CLAIM, String.class);
            TokenPayload payload = TokenPayload.of(
                    Long.parseLong(claims.getSubject()),
                    role != null ? role : "CUSTOMER",
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant(),
                    claims.getId()
            );

            if (payload.isExpired()) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return payload;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public TokenPayload parseClaimsForLogout(String token) {
        try {
            Claims claims = parseClaims(token);
            String role = claims.get(ROLE_CLAIM, String.class);
            return TokenPayload.of(
                    Long.parseLong(claims.getSubject()),
                    role != null ? role : "CUSTOMER",
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant(),
                    claims.getId()
            );
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
