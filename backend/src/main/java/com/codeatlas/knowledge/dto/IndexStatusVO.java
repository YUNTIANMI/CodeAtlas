package com.codeatlas.knowledge.dto;

/**
 * 知识库索引状态。
 */
public class IndexStatusVO {

    private long totalChunks;

    private long indexedChunks;

    private long pendingChunks;

    private String collection;

    private String embeddingModel;

    public IndexStatusVO() {
    }

    public IndexStatusVO(long totalChunks, long indexedChunks, long pendingChunks,
                         String collection, String embeddingModel) {
        this.totalChunks = totalChunks;
        this.indexedChunks = indexedChunks;
        this.pendingChunks = pendingChunks;
        this.collection = collection;
        this.embeddingModel = embeddingModel;
    }

    public long getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(long totalChunks) {
        this.totalChunks = totalChunks;
    }

    public long getIndexedChunks() {
        return indexedChunks;
    }

    public void setIndexedChunks(long indexedChunks) {
        this.indexedChunks = indexedChunks;
    }

    public long getPendingChunks() {
        return pendingChunks;
    }

    public void setPendingChunks(long pendingChunks) {
        this.pendingChunks = pendingChunks;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
}
