package com.codeatlas.auth;

import com.codeatlas.auth.dto.LoginRequest;
import com.codeatlas.auth.dto.RegisterRequest;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.user.dto.UserVO;
import com.codeatlas.user.entity.Role;
import com.codeatlas.user.entity.User;
import com.codeatlas.user.repository.RoleRepository;
import com.codeatlas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试：覆盖正常流程、异常流程与边界情况。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private TokenBlacklistService blacklistService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setUsername("alice");
        sampleUser.setEmail("alice@example.com");
        sampleUser.setPasswordHash("hashed-password");
        sampleUser.setStatus(User.STATUS_ENABLED);
    }

    @Test
    @DisplayName("注册成功：写入 BCrypt 哈希并赋予默认角色")
    void registerSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(roleRepository.findByName(Role.ROLE_USER))
                .thenReturn(Optional.of(new Role(Role.ROLE_USER, "普通用户")));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserVO result = authService.register(request);

        assertNotNull(result);
        assertEquals("alice", result.getUsername());
        assertEquals("alice@example.com", result.getEmail());
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("注册失败：用户名已存在")
    void registerDuplicateUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("alice")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request));
        assertEquals(ErrorCode.USERNAME_EXISTS, ex.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("注册失败：邮箱已被注册")
    void registerDuplicateEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request));
        assertEquals(ErrorCode.EMAIL_EXISTS, ex.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("登录成功：返回 Token 并更新最后登录时间")
    void loginSuccess() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        when(userRepository.findByUsernameOrEmail("alice", "alice"))
                .thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(tokenProvider.generateToken(any())).thenReturn("jwt-token");
        when(tokenProvider.getExpirationSeconds()).thenReturn(7200L);

        var response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals(7200L, response.getExpiresIn());
        assertNotNull(sampleUser.getLastLoginAt());
    }

    @Test
    @DisplayName("登录失败：密码错误（不区分用户不存在，避免账号枚举）")
    void loginBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong-password");

        doThrow(new BadCredentialsException("bad credentials"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
    }

    @Test
    @DisplayName("登录失败：账号已被禁用")
    void loginDisabledAccount() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        doThrow(new DisabledException("disabled"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(request));
        assertEquals(ErrorCode.USER_DISABLED, ex.getErrorCode());
    }

    @Test
    @DisplayName("退出登录：将 Token 加入黑名单")
    void logoutAddsTokenToBlacklist() {
        when(tokenProvider.getRemainingSeconds("jwt-token")).thenReturn(3600L);

        authService.logout("jwt-token");

        verify(blacklistService).add("jwt-token", 3600L);
    }

    @Test
    @DisplayName("退出登录：Token 为空时不做任何操作")
    void logoutWithBlankToken() {
        authService.logout("   ");

        verify(blacklistService, never()).add(anyString(), anyLong());
    }
}
