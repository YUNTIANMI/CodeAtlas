package com.codeatlas.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Token 黑名单：解决 JWT 无法主动失效的问题（见 ADR-003）。
 *
 * <p>退出登录时将 Token 写入 Redis，TTL 为其剩余有效期。
 * Redis 不可用时降级处理：仅记录日志，不阻断请求。
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    private static final String KEY_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void add(String token, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + token, "1", Duration.ofSeconds(ttlSeconds));
        } catch (Exception ex) {
            log.warn("写入 Token 黑名单失败，Redis 可能不可用 | message={}", ex.getMessage());
        }
    }

    public boolean contains(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
        } catch (Exception ex) {
            log.warn("读取 Token 黑名单失败，Redis 可能不可用 | message={}", ex.getMessage());
            return false;
        }
    }
}
