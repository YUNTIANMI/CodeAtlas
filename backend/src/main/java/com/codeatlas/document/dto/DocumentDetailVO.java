package com.codeatlas.document.dto;

/**
 * 文档详情：在列表视图基础上附带解析后的正文与切分块数量。
 */
public class DocumentDetailVO extends DocumentVO {

    private String content;

    private int chunkCount;

    public static DocumentDetailVO from(DocumentVO base, String content, int chunkCount) {
        DocumentDetailVO vo = new DocumentDetailVO();
        vo.setId(base.getId());
        vo.setProjectId(base.getProjectId());
        vo.setName(base.getName());
        vo.setFileType(base.getFileType());
        vo.setFileSize(base.getFileSize());
        vo.setVersion(base.getVersion());
        vo.setIndexed(base.getIndexed());
        vo.setUploadedBy(base.getUploadedBy());
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
