package com.codeatlas.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 登录失败限流测试：验证密码暴力破解防护。
 *
 * <p>对应 docs/development.md 第 13 节（Phase 11：安全）的「登录认证」检查项。
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final String ACCOUNT = "alice";

    private static final String KEY = "auth:login:fail:alice";

    private static final int MAX_ATTEMPTS = 5;

    private static final long LOCK_SECONDS = 900L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(redisTemplate, MAX_ATTEMPTS, LOCK_SECONDS);
    }

    /** 声明使用 Redis 值操作，并返回计数器当前值。 */
    private void givenCounter(String value) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(value);
    }

    /** 声明使用 Redis 值操作，并返回本次自增后的计数。 */
    private void givenIncrement(long newValue) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(KEY)).thenReturn(newValue);
    }

    @Test
    @DisplayName("无失败记录时账号未被锁定")
    void notBlockedWithoutRecord() {
        givenCounter(null);

        assertFalse(service.isBlocked(ACCOUNT));
    }

    @Test
    @DisplayName("失败次数未达上限时账号未被锁定")
    void notBlockedBelowThreshold() {
        givenCounter("4");

        assertFalse(service.isBlocked(ACCOUNT));
    }

    @Test
    @DisplayName("失败次数达到上限时账号被锁定")
    void blockedWhenReachingThreshold() {
        givenCounter("5");

        assertTrue(service.isBlocked(ACCOUNT));
    }

    @Test
    @DisplayName("失败次数超过上限仍然锁定，计数异常值不会误锁")
    void blockedAboveThresholdAndTolerateBadValue() {
        givenCounter("9");
        assertTrue(service.isBlocked(ACCOUNT));

        givenCounter("not-a-number");
        assertFalse(service.isBlocked(ACCOUNT));
    }

    @Test
    @DisplayName("首次失败时设置锁定窗口 TTL，后续失败沿用同一窗口")
    void recordFailureSetsTtlOnlyOnFirstFailure() {
        givenIncrement(1L);
        assertEquals(1, service.recordFailure(ACCOUNT));

        givenIncrement(2L);
        assertEquals(2, service.recordFailure(ACCOUNT));

        // 只设置一次 TTL，锁定周期从首次失败开始计算
        verify(redisTemplate, times(1)).expire(eq(KEY), any(Duration.class));
        verify(redisTemplate).expire(KEY, Duration.ofSeconds(LOCK_SECONDS));
    }

    @Test
    @DisplayName("账号名大小写与空格归一，避免绕过限流")
    void accountKeyIsNormalized() {
        givenCounter("5");

        assertTrue(service.isBlocked("ALICE"));
        assertTrue(service.isBlocked("  alice  "));
    }

    @Test
    @DisplayName("登录成功后清零失败计数")
    void resetClearsCounter() {
        service.reset(ACCOUNT);

        verify(redisTemplate).delete(KEY);
    }

    @Test
    @DisplayName("空账号不参与计数，避免匿名请求互相影响")
    void blankAccountIsIgnored() {
        assertFalse(service.isBlocked(null));
        assertFalse(service.isBlocked("   "));
        assertEquals(0, service.recordFailure(null));
        service.reset(null);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Redis 不可用时降级放行，不阻断正常登录")
    void redisFailureDegradesGracefully() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenThrow(new RuntimeException("redis down"));
        assertFalse(service.isBlocked(ACCOUNT));

        when(valueOperations.increment(KEY)).thenThrow(new RuntimeException("redis down"));
        assertEquals(0, service.recordFailure(ACCOUNT));
    }
}
