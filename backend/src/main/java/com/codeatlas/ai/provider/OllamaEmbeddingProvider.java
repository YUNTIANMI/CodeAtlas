package com.codeatlas.ai.provider;

import com.codeatlas.ai.config.AiProperties;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地 Ollama 向量化实现。
 *
 * <p>使用 Ollama 的 /api/embed 批量接口，默认模型 bge-m3（1024 维）。
 * 优势：免费、无需 API Key、可离线运行，适合个人开发与联调。
 */
@Component
@ConditionalOnProperty(name = "codeatlas.ai.embedding.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingProvider.class);

    private final AiProperties.Embedding properties;

    private final RestClient restClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaEmbeddingProvider(AiProperties properties) {
        this.properties = properties.getEmbedding();
        this.restClient = RestClient.builder()
                .baseUrl(this.properties.getBaseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "input", texts
        );

        String response;
        try {
            response = restClient.post()
                    .uri("/api/embed")
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.error("Ollama 向量化调用失败，请确认服务已启动且模型已拉取 | model={}",
                    properties.getModel(), ex);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                    "向量化服务调用失败，请确认 Ollama 已启动并已拉取模型 " + properties.getModel());
        }

        try {
            JsonNode embeddings = objectMapper.readTree(response).path("embeddings");
            List<List<Double>> result = new ArrayList<>();
            for (JsonNode item : embeddings) {
                List<Double> vector = new ArrayList<>();
                for (JsonNode value : item) {
                    vector.add(value.asDouble());
                }
                result.add(vector);
            }
            if (result.size() != texts.size()) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "向量化返回数量与输入不一致");
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "向量化响应解析失败");
        }
    }

    @Override
    public int dimension() {
        return properties.getDimension();
    }

    @Override
    public String modelName() {
        return "ollama:" + properties.getModel();
    }
}
