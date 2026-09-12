package com.codeatlas.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

    private final String secret;

    private final long expirationSeconds;

    public JwtTokenProvider(@Value("${codeatlas.jwt.secret}") String secret,
                            @Value("${codeatlas.jwt.expiration}") long expirationSeconds) {
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
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
