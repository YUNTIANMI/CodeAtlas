package com.codeatlas.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 提问请求。
 */
public class ChatRequest {

    /** 为空表示开启新会话。 */
    private Long conversationId;

    @NotBlank(message = "提问内容不能为空")
    private String question;

    private Integer topK = 5;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
