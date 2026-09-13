package com.codeatlas.chat.dto;

import com.codeatlas.chat.entity.Message;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息视图对象：附带该回答的引用来源，前端据此渲染"来源：xxx.java"。
 */
public class MessageVO {

    private Long id;

    private Long conversationId;

    private String role;

    private String content;

    private String model;

    private LocalDateTime createdAt;

    private List<CitationItem> citations = List.of();

    public static MessageVO from(Message message, List<CitationItem> citations) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setRole(message.getRole().name());
        vo.setContent(message.getContent());
        vo.setModel(message.getModel());
        vo.setCreatedAt(message.getCreatedAt());
        vo.setCitations(citations == null ? List.of() : citations);
        return vo;
    }

    /** 引用来源项。 */
    public record CitationItem(String sourceType, Long sourceId, String fileName,
                               Integer chunkIndex, Double score, String snippet) {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public List<CitationItem> getCitations() {
        return citations;
    }

    public void setCitations(List<CitationItem> citations) {
        this.citations = citations;
    }
}
