package com.codeatlas.chat.dto;

import com.codeatlas.chat.entity.Conversation;

import java.time.LocalDateTime;

/**
 * 会话视图对象。
 */
public class ConversationVO {

    private Long id;

    private Long projectId;

    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static ConversationVO from(Conversation conversation) {
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setProjectId(conversation.getProjectId());
        vo.setTitle(conversation.getTitle());
        vo.setCreatedAt(conversation.getCreatedAt());
        vo.setUpdatedAt(conversation.getUpdatedAt());
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
