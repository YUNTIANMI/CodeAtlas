package com.codeatlas.ai.provider;

import com.codeatlas.ai.config.AiProperties;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 对话实现（OpenAI 兼容接口）。
 *
 * <p>API Key 通过环境变量 DEEPSEEK_API_KEY 注入。
 */
@Component
@ConditionalOnProperty(name = "codeatlas.ai.chat.provider", havingValue = "deepseek")
public class DeepSeekChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatProvider.class);

    private final AiProperties.Chat properties;

    private final RestClient restClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeepSeekChatProvider(AiProperties properties) {
        this.properties = properties.getChat();
        if (!hasApiKey()) {
            log.warn("未配置 DEEPSEEK_API_KEY 环境变量，AI 问答功能将不可用；"
                    + "请设置该变量后重启后端（PowerShell 示例：$env:DEEPSEEK_API_KEY=\"sk-xxx\"）");
        }
        this.restClient = RestClient.builder()
                .baseUrl(this.properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.properties.getApiKey())
                .build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return callApi(List.of(
                Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
    }

    @Override
    public String chat(String systemPrompt, List<String[]> history, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt));

        if (history != null) {
            for (String[] turn : history) {
                if (turn == null || turn.length < 2) {
                    continue;
                }
                String role = "ASSISTANT".equalsIgnoreCase(turn[0]) ? "assistant" : "user";
                messages.add(Map.of("role", role, "content", turn[1] == null ? "" : turn[1]));
            }
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        return callApi(messages);
    }

    /** 是否已配置可用的 API Key。 */
    private boolean hasApiKey() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    private String callApi(List<? extends Map<String, String>> messages) {
        if (!hasApiKey()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                    "未配置 DEEPSEEK_API_KEY 环境变量，AI 问答不可用；"
                            + "请在启动后端前设置该环境变量（详见 README 的 AI 配置说明）");
        }

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", messages,
                "stream", false);

        String response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.error("DeepSeek 调用失败 | model={}", properties.getModel(), ex);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务调用失败：" + ex.getMessage());
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode()) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 返回内容为空");
            }
            return content.asText();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 响应解析失败");
        }
    }

    @Override
    public String modelName() {
        return "deepseek:" + properties.getModel();
    }
}
