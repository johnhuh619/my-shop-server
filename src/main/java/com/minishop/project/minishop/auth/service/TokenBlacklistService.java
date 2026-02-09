package com.minishop.project.minishop.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, Instant expiresAt) {
        try {
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            if (ttl.isNegative() || ttl.isZero()) {
                return;
            }
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "blacklisted", ttl);
        } catch (Exception e) {
            log.warn("Failed to blacklist token jti={}: {}", jti, e.getMessage());
        }
    }

    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception e) {
            log.warn("Failed to check blacklist for jti={}: {}. Allowing request (fail-open).", jti, e.getMessage());
            return false;
        }
    }
}
