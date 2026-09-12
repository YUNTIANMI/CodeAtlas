package com.codeatlas.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 AI 配置绑定。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}
