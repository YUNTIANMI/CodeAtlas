package com.codeatlas.knowledge.dto;

/**
 * 检索命中的片段。
 */
public class SearchResultVO {

    private Long chunkId;

    private String content;

    private double score;

    private String sourceType;

    private Long sourceId;

    private Integer chunkIndex;

    public SearchResultVO() {
    }

    public SearchResultVO(Long chunkId, String content, double score,
                          String sourceType, Long sourceId, Integer chunkIndex) {
        this.chunkId = chunkId;
        this.content = content;
        this.score = score;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.chunkIndex = chunkIndex;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }
}
