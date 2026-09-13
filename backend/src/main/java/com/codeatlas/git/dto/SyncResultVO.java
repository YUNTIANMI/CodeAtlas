package com.codeatlas.git.dto;

/**
 * 同步结果。
 */
public class SyncResultVO {

    private int total;

    private int added;

    private int skipped;

    public SyncResultVO() {
    }

    public SyncResultVO(int total, int added, int skipped) {
        this.total = total;
        this.added = added;
        this.skipped = skipped;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getAdded() {
        return added;
    }

    public void setAdded(int added) {
        this.added = added;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }
}
