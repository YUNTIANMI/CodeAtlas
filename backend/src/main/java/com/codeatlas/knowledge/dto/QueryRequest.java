package com.codeatlas.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 检索 / 问答请求。
 */
public class QueryRequest {

    @NotBlank(message = "问题不能为空")
    private String query;

    private Integer topK = 5;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
