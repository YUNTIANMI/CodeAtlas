package com.codeatlas.auth;

import com.codeatlas.user.entity.Role;
import com.codeatlas.user.entity.User;
import com.codeatlas.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Security 用户加载。
 *
 * <p>支持使用用户名或邮箱登录。
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + usernameOrEmail));

        return new AuthUser(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.isEnabled(), resolveAuthorities(user));
    }

    /** 无角色时默认赋予 ROLE_USER，避免空权限导致鉴权异常。 */
    private List<SimpleGrantedAuthority> resolveAuthorities(User user) {
        Set<Role> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            return List.of(new SimpleGrantedAuthority(Role.ROLE_USER));
        }
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());
    }
}
