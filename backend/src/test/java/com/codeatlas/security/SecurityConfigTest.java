package com.codeatlas.security;

import com.codeatlas.auth.JwtAuthenticationFilter;
import com.codeatlas.auth.UserDetailsServiceImpl;
import com.codeatlas.common.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * CORS 安全配置测试（Phase 11）。
 *
 * <p>跨域白名单一旦写成通配，任意站点都能借用已登录用户的凭据发起请求，
 * 因此这里锁定「只允许配置中声明的来源」这一行为。
 */
class SecurityConfigTest {

    private CorsConfiguration corsOf(String allowedOrigins) {
        SecurityConfig config = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(UserDetailsServiceImpl.class),
                allowedOrigins);

        CorsConfigurationSource source = config.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(cors);
        return cors;
    }

    @Test
    @DisplayName("白名单来自配置项，且不含通配符")
    void allowedOriginsComeFromConfiguration() {
        CorsConfiguration cors = corsOf(
                "http://localhost:5173, https://codeatlas.example.com");

        assertEquals(List.of("http://localhost:5173", "https://codeatlas.example.com"),
                cors.getAllowedOriginPatterns());
        assertFalse(cors.getAllowedOriginPatterns().contains("*"));
    }

    @Test
    @DisplayName("未在白名单中的来源被拒绝")
    void unknownOriginIsRejected() {
        CorsConfiguration cors = corsOf("http://localhost:5173");

        assertEquals("http://localhost:5173", cors.checkOrigin("http://localhost:5173"));
        assertNull(cors.checkOrigin("http://evil.example.com"));
        assertNull(cors.checkOrigin("http://localhost:8080"));
    }

    @Test
    @DisplayName("允许携带凭据，且不放开 TRACE 等方法")
    void credentialsAllowedWithoutTraceMethod() {
        CorsConfiguration cors = corsOf("http://localhost:5173");

        assertEquals(Boolean.TRUE, cors.getAllowCredentials());
        assertFalse(cors.getAllowedMethods().contains("TRACE"));
        assertTrue(cors.getAllowedMethods().containsAll(List.of("GET", "POST", "DELETE")));
    }

    @Test
    @DisplayName("配置中的空项被忽略，避免因多余逗号放开空来源")
    void blankOriginsAreIgnored() {
        CorsConfiguration cors = corsOf(" http://localhost:5173 ,, ");

        assertEquals(List.of("http://localhost:5173"), cors.getAllowedOriginPatterns());
    }
}
