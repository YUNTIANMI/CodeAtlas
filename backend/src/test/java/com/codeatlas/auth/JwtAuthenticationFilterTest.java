package com.codeatlas.auth;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JWT 认证过滤器安全测试。
 *
 * <p>对应 docs/development.md 第 13 节（Phase 11：安全）中的「登录认证」与「Token」检查项。
 * 认证的唯一入口就是这个过滤器，一旦它的放行条件被绕过，
 * 后面所有基于 userId 的权限校验都会失去意义。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "codeatlas-test-secret-key-please-use-at-least-32-bytes-long";

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private TokenBlacklistService blacklistService;

    private JwtTokenProvider tokenProvider;

    private JwtAuthenticationFilter filter;

    private FilterChain filterChain;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 7200L);
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService, blacklistService);
        filterChain = mock(FilterChain.class);

        userDetails = new AuthUser(1L, "alice", "hashed-password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 执行过滤器并返回最终写入 SecurityContext 的认证信息。 */
    private Authentication runFilter() throws Exception {
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private void givenValidToken(String header) {
        request.addHeader(HttpHeaders.AUTHORIZATION, header);
    }

    @Test
    @DisplayName("无 Authorization 头：不建立认证，请求继续向下")
    void noAuthorizationHeader() throws Exception {
        Authentication auth = runFilter();

        assertNull(auth);
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    @Test
    @DisplayName("认证方案不是 Bearer：不建立认证")
    void nonBearerScheme() throws Exception {
        givenValidToken("Basic YWxpY2U6cGFzc3dvcmQ=");

        assertNull(runFilter());
    }

    @Test
    @DisplayName("合法 Token：认证成功且主体为 AuthUser，可取出 userId")
    void validToken() throws Exception {
        givenValidToken("Bearer " + tokenProvider.generateToken(userDetails));
        when(blacklistService.contains(any())).thenReturn(false);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        Authentication auth = runFilter();

        assertNotNull(auth);
        AuthUser principal = assertInstanceOf(AuthUser.class, auth.getPrincipal());
        assertEquals(1L, principal.getUserId());
        assertEquals("alice", principal.getUsername());
    }

    @Test
    @DisplayName("签名被篡改的 Token：不建立认证")
    void tamperedToken() throws Exception {
        String token = tokenProvider.generateToken(userDetails);
        givenValidToken("Bearer " + token.substring(0, token.length() - 2) + "xy");

        assertNull(runFilter());
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    @Test
    @DisplayName("他人用其它密钥签发的伪造 Token：不建立认证")
    void forgedTokenWithAnotherKey() throws Exception {
        JwtTokenProvider attackerProvider = new JwtTokenProvider(
                "attacker-controlled-secret-key-with-enough-length-000000", 7200L);
        givenValidToken("Bearer " + attackerProvider.generateToken(userDetails));

        assertNull(runFilter());
    }

    @Test
    @DisplayName("已过期 Token：不建立认证")
    void expiredToken() throws Exception {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -60L);
        givenValidToken("Bearer " + expiredProvider.generateToken(userDetails));

        assertNull(runFilter());
    }

    @Test
    @DisplayName("乱码 Token：不建立认证且不抛异常")
    void malformedToken() throws Exception {
        givenValidToken("Bearer not-a-jwt-at-all");

        assertNull(runFilter());
    }

    @Test
    @DisplayName("已退出登录（黑名单）的 Token：不建立认证")
    void blacklistedToken() throws Exception {
        givenValidToken("Bearer " + tokenProvider.generateToken(userDetails));
        when(blacklistService.contains(any())).thenReturn(true);

        assertNull(runFilter());
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    @Test
    @DisplayName("Token 有效但用户已被删除：加载用户失败时按未认证处理（fail-closed）")
    void userNotFound() throws Exception {
        givenValidToken("Bearer " + tokenProvider.generateToken(userDetails));
        when(blacklistService.contains(any())).thenReturn(false);
        when(userDetailsService.loadUserByUsername("alice"))
                .thenThrow(new UsernameNotFoundException("用户不存在：alice"));

        assertNull(runFilter());
    }

    @Test
    @DisplayName("请求已带认证信息时不重复认证，避免 Token 覆盖已有身份")
    void doesNotOverrideExistingAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("existing", null, List.of()));
        givenValidToken("Bearer " + tokenProvider.generateToken(userDetails));

        Authentication auth = runFilter();

        assertEquals("existing", auth.getPrincipal());
        verify(userDetailsService, never()).loadUserByUsername(any());
    }
}
