package com.codeatlas.knowledge.dto;

/**
 * 引用来源：回答必须能追溯到具体的文件与块。
 */
public class CitationVO {

    private String sourceType;

    private Long sourceId;

    private Integer chunkIndex;

    private double score;

    private String snippet;

    public CitationVO() {
    }

    public CitationVO(String sourceType, Long sourceId, Integer chunkIndex,
                      double score, String snippet) {
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.chunkIndex = chunkIndex;
        this.score = score;
        this.snippet = snippet;
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

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
