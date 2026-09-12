package com.codeatlas.document.dto;

import com.codeatlas.document.entity.Document;

import java.time.LocalDateTime;

/**
 * 文档视图对象。
 */
public class DocumentVO {

    private Long id;

    private Long projectId;

    private String name;

    private String fileType;

    private Long fileSize;

    private Integer version;

    private Boolean indexed;

    private Long uploadedBy;

    private LocalDateTime createdAt;

    public static DocumentVO from(Document document) {
        DocumentVO vo = new DocumentVO();
        vo.setId(document.getId());
        vo.setProjectId(document.getProjectId());
        vo.setName(document.getName());
        vo.setFileType(document.getFileType());
        vo.setFileSize(document.getFileSize());
        vo.setVersion(document.getVersion());
        vo.setIndexed(document.getIndexed());
        vo.setUploadedBy(document.getUploadedBy());
        vo.setCreatedAt(document.getCreatedAt());
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getIndexed() {
        return indexed;
    }

    public void setIndexed(Boolean indexed) {
        this.indexed = indexed;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
