package com.codeatlas.common.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：验证数据库与 Redis 连通性，用于确认基础工程可用。
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;

    private final StringRedisTemplate redisTemplate;

    public HealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("db", checkDatabase());
        result.put("redis", checkRedis());
        return result;
    }

    private String checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "up";
        } catch (Exception ex) {
            log.error("数据库健康检查失败", ex);
            return "down";
        }
    }

    private String checkRedis() {
        try {
            // 能成功执行一次查询即视为连通，不可用时由下方 catch 捕获
            redisTemplate.hasKey("health:probe");
            return "up";
        } catch (Exception ex) {
            log.error("Redis 健康检查失败", ex);
            return "down";
        }
    }
}
