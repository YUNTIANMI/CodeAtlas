package com.codeatlas.ai.qdrant;

import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Qdrant REST 客户端。
 *
 * <p>仅封装本项目需要的四个能力：建集合、写入点、按项目检索、按项目删除。
 */
@Component
public class QdrantClient {

    private static final Logger log = LoggerFactory.getLogger(QdrantClient.class);

    private final String collection;

    private final RestClient restClient;

    public QdrantClient(@Value("${codeatlas.qdrant.host:localhost}") String host,
                        @Value("${codeatlas.qdrant.port:6333}") int port,
                        @Value("${codeatlas.qdrant.collection:codeatlas}") String collection) {
        this.collection = collection;
        this.restClient = RestClient.builder()
                .baseUrl("http://" + host + ":" + port)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 创建集合（已存在时忽略），向量维度必须与 Embedding 模型一致。 */
    public void ensureCollection(int dimension) {
        Map<String, Object> body = Map.of(
                "vectors", Map.of("size", dimension, "distance", "Cosine")
        );
        try {
            restClient.put()
                    .uri("/collections/" + collection)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Qdrant 集合已就绪 | collection={} | dimension={}", collection, dimension);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VECTOR_STORE_UNAVAILABLE,
                    "向量库不可用，请确认 Qdrant 已启动");
        }
    }

    /** 写入向量点，payload 携带项目与来源信息，用于过滤与溯源。 */
    public void upsert(List<VectorPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<Map<String, Object>> body = new ArrayList<>();
        for (VectorPoint point : points) {
            body.add(Map.of(
                    "id", point.id(),
                    "vector", point.vector(),
                    "payload", point.payload()
            ));
        }
        try {
            restClient.put()
                    .uri("/collections/" + collection + "/points?wait=true")
                    .body(Map.of("points", body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VECTOR_STORE_UNAVAILABLE, "向量写入失败");
        }
    }

    /**
     * 按项目过滤检索相似向量。
     *
     * @param vector 查询向量
     * @param limit  返回条数
     * @param projectId 项目 ID（必须过滤，保证项目数据隔离）
     */
    @SuppressWarnings("unchecked")
    public List<SearchHit> search(List<Double> vector, int limit, Long projectId) {
        Map<String, Object> filter = Map.of(
                "must", List.of(
                        Map.of("key", "project_id", "match", Map.of("value", projectId))
                )
        );
        Map<String, Object> body = Map.of(
                "vector", vector,
                "limit", limit,
                "filter", filter,
                "with_payload", true
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/collections/" + collection + "/points/search")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            List<SearchHit> hits = new ArrayList<>();
            Object result = response == null ? null : response.get("result");
            if (result instanceof List<?> list) {
                for (Object item : list) {
                    Map<String, Object> map = (Map<String, Object>) item;
                    Number id = (Number) map.get("id");
                    Number score = (Number) map.get("score");
                    Map<String, Object> payload =
                            (Map<String, Object>) map.getOrDefault("payload", Map.of());
                    hits.add(new SearchHit(
                            id == null ? null : id.longValue(),
                            score == null ? 0 : score.doubleValue(),
                            payload));
                }
            }
            return hits;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VECTOR_STORE_UNAVAILABLE, "向量检索失败");
        }
    }

    /** 删除某项目的全部向量。 */
    public void deleteByProject(Long projectId) {
        Map<String, Object> body = Map.of(
                "filter", Map.of(
                        "must", List.of(
                                Map.of("key", "project_id", "match", Map.of("value", projectId))
                        )
                )
        );
        try {
            restClient.post()
                    .uri("/collections/" + collection + "/points/delete?wait=true")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VECTOR_STORE_UNAVAILABLE, "向量删除失败");
        }
    }

    public String getCollection() {
        return collection;
    }

    /** 向量点。 */
    public record VectorPoint(Long id, List<Double> vector, Map<String, Object> payload) {
    }

    /** 检索结果。 */
    public record SearchHit(Long id, double score, Map<String, Object> payload) {
    }
}
