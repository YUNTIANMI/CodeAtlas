package com.codeatlas.user.controller;

import com.codeatlas.common.Result;
import com.codeatlas.user.dto.UserVO;
import com.codeatlas.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口。
 *
 * <pre>
 * GET /api/v1/users/me  获取当前登录用户信息（需认证）
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Result<UserVO> me(Authentication authentication) {
        // authentication 由 JwtAuthenticationFilter 写入，已通过认证的一定非空
        return Result.success(userService.getByUsername(authentication.getName()));
    }
}
