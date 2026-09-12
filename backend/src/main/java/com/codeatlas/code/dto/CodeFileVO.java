package com.codeatlas.code.dto;

import com.codeatlas.code.entity.CodeFile;

import java.time.LocalDateTime;

/**
 * 代码文件视图对象（不含正文，避免列表响应过大）。
 */
public class CodeFileVO {

    private Long id;

    private Long projectId;

    private String filePath;

    private String fileName;

    private String language;

    private Long fileSize;

    private Boolean indexed;

    private LocalDateTime createdAt;

    public static CodeFileVO from(CodeFile codeFile) {
        CodeFileVO vo = new CodeFileVO();
        vo.setId(codeFile.getId());
        vo.setProjectId(codeFile.getProjectId());
        vo.setFilePath(codeFile.getFilePath());
        vo.setFileName(codeFile.getFileName());
        vo.setLanguage(codeFile.getLanguage());
        vo.setFileSize(codeFile.getFileSize());
        vo.setIndexed(codeFile.getIndexed());
        vo.setCreatedAt(codeFile.getCreatedAt());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Boolean getIndexed() {
        return indexed;
    }

    public void setIndexed(Boolean indexed) {
        this.indexed = indexed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
