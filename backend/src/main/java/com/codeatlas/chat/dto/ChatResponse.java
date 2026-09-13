package com.codeatlas.chat.dto;

/**
 * 提问响应：会话 ID + 助手消息（含引用）。
 */
public class ChatResponse {

    private Long conversationId;

    private MessageVO message;

    public ChatResponse() {
    }

    public ChatResponse(Long conversationId, MessageVO message) {
        this.conversationId = conversationId;
        this.message = message;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public MessageVO getMessage() {
        return message;
    }

    public void setMessage(MessageVO message) {
        this.message = message;
    }
}
