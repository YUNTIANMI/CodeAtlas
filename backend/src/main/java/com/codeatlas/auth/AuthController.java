package com.codeatlas.auth;

import com.codeatlas.auth.dto.LoginRequest;
import com.codeatlas.auth.dto.LoginResponse;
import com.codeatlas.auth.dto.RegisterRequest;
import com.codeatlas.common.Result;
import com.codeatlas.user.dto.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <pre>
 * POST /api/v1/auth/register  注册
 * POST /api/v1/auth/login     登录
 * POST /api/v1/auth/logout    退出
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authService.logout(header.substring(BEARER_PREFIX.length()));
        }
        return Result.success();
    }
}
