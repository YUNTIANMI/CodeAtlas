package com.codeatlas.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 登录失败限流：防止针对账号的密码暴力破解。
 *
 * <p>策略：同一账号连续失败达到 {@code max-attempts} 次后锁定 {@code lock-seconds} 秒，
 * 期间登录直接返回 429，不再执行密码校验，避免给攻击者继续试探的机会。
 * 登录成功后立即清零。
 *
 * <p>计数存放在 Redis 并带 TTL，天然过期，不会无限累积。
 * Redis 不可用时降级放行（只记日志），保证可用性优先——
 * 项目本身仍受「账号锁定」之外的其它防线保护。
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** 按账号计数；账号由调用方统一小写归一。 */
    private static final String KEY_PREFIX = "auth:login:fail:";

    private final StringRedisTemplate redisTemplate;

    private final int maxAttempts;

    private final long lockSeconds;

    public LoginAttemptService(StringRedisTemplate redisTemplate,
                               @Value("${codeatlas.security.login.max-attempts:5}") int maxAttempts,
                               @Value("${codeatlas.security.login.lock-seconds:900}") long lockSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.lockSeconds = lockSeconds;
    }

    /** 当前账号是否已被锁定。 */
    public boolean isBlocked(String account) {
        String key = key(account);
        if (key == null) {
            return false;
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return false;
            }
            return parseInt(value) >= maxAttempts;
        } catch (Exception ex) {
            log.warn("读取登录失败计数失败，降级放行 | message={}", ex.getMessage());
            return false;
        }
    }

    /** 记录一次失败，返回累计失败次数（0 表示计数不可用）。 */
    public int recordFailure(String account) {
        String key = key(account);
        if (key == null) {
            return 0;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 只在第一次失败时设置窗口，保证锁定周期从首次失败开始计算
                redisTemplate.expire(key, Duration.ofSeconds(lockSeconds));
            }
            if (count != null && count >= maxAttempts) {
                log.warn("账号因连续登录失败被锁定 | account={} | attempts={} | lockSeconds={}",
                        account, count, lockSeconds);
            }
            return count == null ? 0 : count.intValue();
        } catch (Exception ex) {
            log.warn("写入登录失败计数失败，降级放行 | message={}", ex.getMessage());
            return 0;
        }
    }

    /** 登录成功后清零。 */
    public void reset(String account) {
        String key = key(account);
        if (key == null) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("清除登录失败计数失败 | message={}", ex.getMessage());
        }
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getLockSeconds() {
        return lockSeconds;
    }

    /** 空账号不计数，避免空字符串被当作一个公共 key 互相影响。 */
    private String key(String account) {
        if (!StringUtils.hasText(account)) {
            return null;
        }
        return KEY_PREFIX + account.trim().toLowerCase();
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
