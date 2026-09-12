package com.codeatlas.code.dto;

/**
 * 代码文件详情：附带源码正文与切分块数量。
 */
public class CodeFileDetailVO extends CodeFileVO {

    private String content;

    private int chunkCount;

    public static CodeFileDetailVO from(CodeFileVO base, String content, int chunkCount) {
        CodeFileDetailVO vo = new CodeFileDetailVO();
        vo.setId(base.getId());
        vo.setProjectId(base.getProjectId());
        vo.setFilePath(base.getFilePath());
        vo.setFileName(base.getFileName());
        vo.setLanguage(base.getLanguage());
        vo.setFileSize(base.getFileSize());
        vo.setIndexed(base.getIndexed());
        vo.setCreatedAt(base.getCreatedAt());
        vo.setContent(content);
        vo.setChunkCount(chunkCount);
        return vo;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }
}
