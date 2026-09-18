package com.codeatlas.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 生成与校验测试。
 */
class JwtTokenProviderTest {

    /** HS256 要求密钥长度不小于 32 字节。 */
    private static final String SECRET =
            "codeatlas-test-secret-key-please-use-at-least-32-bytes-long";

    private JwtTokenProvider tokenProvider;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 7200L);
        userDetails = User.builder()
                .username("alice")
                .password("hashed")
                .authorities("ROLE_USER")
                .build();
    }

    @Test
    @DisplayName("生成的 Token 可解析出正确用户名")
    void generateAndParse() {
        String token = tokenProvider.generateToken(userDetails);

        assertEquals("alice", tokenProvider.getUsername(token));
        assertTrue(tokenProvider.validate(token));
    }

    @Test
    @DisplayName("篡改后的 Token 校验失败")
    void validateTamperedToken() {
        String token = tokenProvider.generateToken(userDetails);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertFalse(tokenProvider.validate(tampered));
        assertNull(tokenProvider.getUsername(tampered));
    }

    @Test
    @DisplayName("过期 Token 校验失败")
    void validateExpiredToken() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1L);
        String token = expiredProvider.generateToken(userDetails);

        assertFalse(expiredProvider.validate(token));
        assertEquals(0L, expiredProvider.getRemainingSeconds(token));
    }

    @Test
    @DisplayName("剩余有效时间计算正确")
    void remainingSeconds() {
        String token = tokenProvider.generateToken(userDetails);
        long remaining = tokenProvider.getRemainingSeconds(token);

        assertTrue(remaining > 0 && remaining <= 7200L);
    }

    @Test
    @DisplayName("显式配置的密钥不会被标记为随机生成")
    void configuredSecretIsNotGenerated() {
        assertFalse(tokenProvider.isGeneratedSecret());
    }

    @Test
    @DisplayName("未配置密钥时生成随机密钥：可正常签发校验，且各实例互不通用")
    void blankSecretFallsBackToRandomKey() {
        JwtTokenProvider first = new JwtTokenProvider(null, 7200L);
        JwtTokenProvider second = new JwtTokenProvider("   ", 7200L);

        assertTrue(first.isGeneratedSecret());
        assertTrue(second.isGeneratedSecret());

        // 关键安全属性：A 签发的 Token 无法被 B 验证通过，
        // 说明两者用的不是同一个（可被公开获知的）固定密钥
        String tokenFromFirst = first.generateToken(userDetails);
        assertTrue(first.validate(tokenFromFirst));
        assertFalse(second.validate(tokenFromFirst));
    }

    @Test
    @DisplayName("使用曾公开的占位密钥：拒绝启动，避免 Token 可被伪造")
    void revokedPlaceholderSecretIsRejected() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(
                "codeatlas-default-secret-please-change-in-production-environment-32bytes", 7200L));
    }

    @Test
    @DisplayName("密钥长度不足 32 字节：拒绝启动")
    void tooShortSecretIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider("too-short-secret", 7200L));
    }
}
