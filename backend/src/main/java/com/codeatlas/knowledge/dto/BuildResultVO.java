package com.codeatlas.knowledge.dto;

/**
 * 知识库构建结果。
 */
public class BuildResultVO {

    private int total;

    private int indexed;

    private int failed;

    public BuildResultVO() {
    }

    public BuildResultVO(int total, int indexed, int failed) {
        this.total = total;
        this.indexed = indexed;
        this.failed = failed;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getIndexed() {
        return indexed;
    }

    public void setIndexed(int indexed) {
        this.indexed = indexed;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }
}
