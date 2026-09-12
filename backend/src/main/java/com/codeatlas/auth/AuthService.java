package com.codeatlas.auth;

import com.codeatlas.auth.dto.LoginRequest;
import com.codeatlas.auth.dto.LoginResponse;
import com.codeatlas.auth.dto.RegisterRequest;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.user.dto.UserVO;
import com.codeatlas.user.entity.Role;
import com.codeatlas.user.entity.User;
import com.codeatlas.user.repository.RoleRepository;
import com.codeatlas.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证服务：注册、登录、退出。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final JwtTokenProvider tokenProvider;

    private final TokenBlacklistService blacklistService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider,
                       TokenBlacklistService blacklistService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.blacklistService = blacklistService;
    }

    /** 注册：校验唯一性后写入 BCrypt 密码哈希，并赋予默认角色 ROLE_USER。 */
    @Transactional
    public UserVO register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName() != null
                ? request.getDisplayName()
                : request.getUsername());
        user.setStatus(User.STATUS_ENABLED);

        Role defaultRole = roleRepository.findByName(Role.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(Role.ROLE_USER, "普通用户")));
        Set<Role> roles = new HashSet<>();
        roles.add(defaultRole);
        user.setRoles(roles);

        User saved = userRepository.save(user);
        log.info("用户注册成功 | userId={} | username={}", saved.getId(), saved.getUsername());
        return UserVO.from(saved);
    }

    /** 登录：校验凭据后签发 Token，并更新最后登录时间。 */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException ex) {
            // 不区分用户不存在与密码错误，避免账号枚举
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        } catch (DisabledException ex) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        User user = userRepository.findByUsernameOrEmail(request.getUsername(), request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        UserDetails userDetails = new AuthUser(user.getId(), user.getUsername(),
                user.getPasswordHash(), user.isEnabled(),
                List.of(new SimpleGrantedAuthority(Role.ROLE_USER)));

        String token = tokenProvider.generateToken(userDetails);
        log.info("用户登录成功 | userId={} | username={}", user.getId(), user.getUsername());

        return new LoginResponse(token, tokenProvider.getExpirationSeconds(), UserVO.from(user));
    }

    /** 退出：将 Token 加入黑名单，TTL 为其剩余有效期。 */
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        long remaining = tokenProvider.getRemainingSeconds(token);
        blacklistService.add(token, remaining);
    }
}
