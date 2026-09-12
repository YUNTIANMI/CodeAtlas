package com.codeatlas.ai.provider;

import java.util.List;

/**
 * 向量化抽象：将文本转换为向量。
 *
 * <p>注意：DeepSeek 等部分厂商只提供对话模型、不提供 Embedding，
 * 因此 Embedding 与 Chat 拆分为两个可独立切换的 Provider。
 */
public interface EmbeddingProvider {

    /** 单条文本向量化。 */
    default List<Double> embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /** 批量向量化，返回顺序与入参一致。 */
    List<List<Double>> embedBatch(List<String> texts);

    /** 向量维度，必须与 Qdrant 集合配置一致。 */
    int dimension();

    /** 模型标识。 */
    String modelName();
}
