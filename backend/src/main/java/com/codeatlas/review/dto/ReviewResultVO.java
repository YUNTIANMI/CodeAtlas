package com.codeatlas.review.dto;

import com.codeatlas.review.entity.ReviewResult;

import java.time.LocalDateTime;

/**
 * 审查结果视图对象：结构化字段，便于前端按等级/文件/类别展示。
 */
public class ReviewResultVO {

    private Long id;

    private Long projectId;

    private String sourceType;

    private String sourceRef;

    private String severity;

    private String category;

    private String filePath;

    private Integer line;

    private String description;

    private String risk;

    private String suggestion;

    private String model;

    private LocalDateTime createdAt;

    public static ReviewResultVO from(ReviewResult result) {
        ReviewResultVO vo = new ReviewResultVO();
        vo.setId(result.getId());
        vo.setProjectId(result.getProjectId());
        vo.setSourceType(result.getSourceType());
        vo.setSourceRef(result.getSourceRef());
        vo.setSeverity(result.getSeverity());
        vo.setCategory(result.getCategory());
        vo.setFilePath(result.getFilePath());
        vo.setLine(result.getLine());
        vo.setDescription(result.getDescription());
        vo.setRisk(result.getRisk());
        vo.setSuggestion(result.getSuggestion());
        vo.setModel(result.getModel());
        vo.setCreatedAt(result.getCreatedAt());
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getLine() {
        return line;
    }

    public void setLine(Integer line) {
        this.line = line;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
