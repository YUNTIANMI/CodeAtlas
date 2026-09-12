package com.codeatlas.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 认证主体：在标准 UserDetails 基础上携带 userId。
 *
 * <p>这样 Controller 无需再按 username 反查数据库即可获取当前用户 ID，
 * 所有涉及 projectId 的权限校验都依赖它。
 */
public class AuthUser extends User {

    private final Long userId;

    public AuthUser(Long userId, String username, String password,
                    Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
    }

    public AuthUser(Long userId, String username, String password, boolean enabled,
                    Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
