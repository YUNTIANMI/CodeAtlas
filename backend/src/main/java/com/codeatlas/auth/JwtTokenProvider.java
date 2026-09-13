package com.codeatlas.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与校验。
 *
 * <p>采用无状态 Token（见 ADR-003），Token 中仅存放 username，
 * 不存放项目角色——项目权限在 Service 层实时校验，避免权限变更延迟生效。
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** HS256 要求密钥至少 32 字节。 */
    private static final int MIN_SECRET_BYTES = 32;

    /** application.yml 中的占位密钥前缀，出现即说明未通过环境变量覆盖。 */
    private static final String DEFAULT_SECRET_PREFIX = "codeatlas-default-secret";

    private final String secret;

    private final long expirationSeconds;

    public JwtTokenProvider(@Value("${codeatlas.jwt.secret}") String secret,
                            @Value("${codeatlas.jwt.expiration}") long expirationSeconds) {
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
        warnIfInsecureSecret(secret);
    }

    /**
     * 启动期检查签名密钥强度。
     *
     * <p>密钥一旦泄露，任何人都可以伪造任意用户（含管理员）的 Token，
     * 因此使用默认占位密钥或过短密钥时必须给出醒目告警。
     */
    private void warnIfInsecureSecret(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            log.warn("JWT 密钥长度不足 {} 字节，存在被暴力破解的风险，请在 JWT_SECRET 中配置强随机密钥",
                    MIN_SECRET_BYTES);
            return;
        }
        if (secret.startsWith(DEFAULT_SECRET_PREFIX)) {
            log.warn("正在使用默认 JWT 密钥，生产环境必须通过环境变量 JWT_SECRET 覆盖，"
                    + "否则任何人都可以伪造 Token");
        }
    }

    /** 生成 Token，subject 为用户名。 */
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /** 解析用户名，失败返回 null。 */
    public String getUsername(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    /** 校验 Token 是否有效（签名正确且未过期）。 */
    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /** 获取 Token 剩余有效秒数，用于退出登录时设置黑名单 TTL。 */
    public long getRemainingSeconds(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (JwtException | IllegalArgumentException ex) {
            return 0;
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
