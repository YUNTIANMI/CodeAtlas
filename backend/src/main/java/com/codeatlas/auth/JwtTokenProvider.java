package com.codeatlas.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * JWT 生成与校验。
 *
 * <p>采用无状态 Token（见 ADR-003），Token 中仅存放 username，
 * 不存放项目角色——项目权限在 Service 层实时校验，避免权限变更延迟生效。
 *
 * <p><b>密钥策略（重要）</b>：本类刻意<b>不提供</b>内置默认密钥。
 * 对称签名（HS256）下密钥就是唯一的身份凭证，默认值一旦写进仓库即等同于公开：
 * 任何人都能用它签发任意用户（含管理员）的 Token，而服务端在数学上无法区分真伪。
 * 因此这里的处理是：
 * <ul>
 *   <li>已显式配置密钥：校验长度与是否命中历史公开占位值，不合规则<b>拒绝启动</b>；</li>
 *   <li>未配置密钥：生成本进程一次性随机密钥并告警——Token 依然不可伪造，
 *       代价是重启后所有登录态失效。</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** HS256 要求密钥至少 32 字节。 */
    private static final int MIN_SECRET_BYTES = 32;

    /** 未配置密钥时生成的随机密钥长度（48 字节 = 384 bit）。 */
    private static final int GENERATED_SECRET_BYTES = 48;

    /**
     * 曾作为「默认值」被自动注入、因而会出现在公开源码里的密钥前缀。
     *
     * <p>这类值最危险：使用者不配置也能跑起来，于是无感知地用一个公开密钥保护系统。
     * 命中即拒绝启动，避免重蹈覆辙。
     *
     * <p>此处<b>不</b>收录测试文件里的密钥常量——测试常量本身也在公开仓库中，
     * 靠「拉黑字符串」无法获得安全性；只拉黑会被真实部署无感知使用的默认值。
     */
    private static final List<String> REVOKED_SECRET_PREFIXES = List.of(
            "codeatlas-default-secret");

    /** 密钥不合规时的统一提示，直接给出可复制的生成命令。 */
    private static final String SECRET_GENERATE_HINT =
            "生成方式：openssl rand -base64 48；"
            + "PowerShell 用 $b=New-Object byte[] 48;"
            + "[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b);"
            + "[Convert]::ToBase64String($b)；"
            + "或运行 ./deploy.sh 自动生成并写入 .env";

    private final String secret;

    private final long expirationSeconds;

    /** 密钥是否由本进程随机生成（即未配置 JWT_SECRET）。 */
    private final boolean generatedSecret;

    public JwtTokenProvider(@Value("${codeatlas.jwt.secret:}") String secret,
                            @Value("${codeatlas.jwt.expiration}") long expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
        if (StringUtils.hasText(secret)) {
            String configured = secret.trim();
            rejectIfInsecure(configured);
            this.secret = configured;
            this.generatedSecret = false;
        } else {
            this.secret = generateRandomSecret();
            this.generatedSecret = true;
            log.warn("未配置 JWT_SECRET，已为本进程生成一次性随机密钥。"
                    + "Token 不可被伪造，但重启会让所有登录态失效，多实例部署时各实例 Token 也不互通。"
                    + "生产 / 长期部署请在 .env 中配置固定密钥。{}", SECRET_GENERATE_HINT);
        }
    }

    /**
     * 启动期校验显式配置的密钥，不合规直接抛异常阻断启动。
     *
     * <p>这里刻意选择 fail-fast 而不是打印告警：告警会被淹没在启动日志里，
     * 而此处配置错误意味着「任何人都能冒充管理员」，属于不可接受的降级。
     *
     * @throws IllegalStateException 密钥命中历史公开占位值，或长度不足 32 字节
     */
    private static void rejectIfInsecure(String secret) {
        for (String revoked : REVOKED_SECRET_PREFIXES) {
            if (secret.startsWith(revoked)) {
                throw new IllegalStateException(
                        "JWT_SECRET 使用了曾作为默认值公开的占位密钥，任何人都可据此伪造 Token，已拒绝启动。"
                        + "请更换为新的强随机密钥。" + SECRET_GENERATE_HINT);
            }
        }
        int length = secret.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(String.format(
                    "JWT_SECRET 过短：当前 %d 字节，HS256 要求至少 %d 字节。%s",
                    length, MIN_SECRET_BYTES, SECRET_GENERATE_HINT));
        }
    }

    /** 生成密码学安全的随机密钥，Base64 编码以便直接写入 .env。 */
    private static String generateRandomSecret() {
        byte[] bytes = new byte[GENERATED_SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** 生成 Token，subject 为用户名。 */
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /** 解析用户名，失败返回 null。 */
    public String getUsername(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    /** 校验 Token 是否有效（签名正确且未过期）。 */
    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /** 获取 Token 剩余有效秒数，用于退出登录时设置黑名单 TTL。 */
    public long getRemainingSeconds(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            long remaining = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (JwtException | IllegalArgumentException ex) {
            return 0;
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /** 密钥是否由本进程随机生成（即未配置 JWT_SECRET）。供健康检查与测试使用。 */
    public boolean isGeneratedSecret() {
        return generatedSecret;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
