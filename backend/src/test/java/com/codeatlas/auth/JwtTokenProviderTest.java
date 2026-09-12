package com.codeatlas.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
