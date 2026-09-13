package com.codeatlas.chat.controller;

import com.codeatlas.auth.AuthUser;
import com.codeatlas.chat.dto.ChatRequest;
import com.codeatlas.chat.dto.ChatResponse;
import com.codeatlas.chat.dto.ConversationVO;
import com.codeatlas.chat.dto.MessageVO;
import com.codeatlas.chat.service.ChatService;
import com.codeatlas.common.Result;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 项目问答接口。
 *
 * <pre>
 * POST   /api/v1/projects/{projectId}/chat          提问（无 conversationId 则新建会话）
 * GET    /api/v1/projects/{projectId}/conversations 我的会话列表
 * GET    /api/v1/conversations/{id}/messages        会话消息（含引用来源）
 * DELETE /api/v1/conversations/{id}                 删除会话
 * </pre>
 */
@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/projects/{projectId}/chat")
    public Result<ChatResponse> chat(@PathVariable Long projectId,
                                     @Valid @RequestBody ChatRequest request,
                                     Authentication authentication) {
        Long userId = currentUserId(authentication);
        MessageVO message = chatService.ask(projectId, userId,
                request.getConversationId(), request.getQuestion(), request.getTopK());
        return Result.success(new ChatResponse(message.getConversationId(), message));
    }

    @GetMapping("/projects/{projectId}/conversations")
    public Result<List<ConversationVO>> conversations(@PathVariable Long projectId,
                                                      Authentication authentication) {
        return Result.success(chatService.listConversations(projectId, currentUserId(authentication)));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long conversationId,
                                            Authentication authentication) {
        return Result.success(chatService.listMessages(conversationId, currentUserId(authentication)));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> delete(@PathVariable Long conversationId,
                               Authentication authentication) {
        chatService.deleteConversation(conversationId, currentUserId(authentication));
        return Result.success();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.getUserId();
        }
        throw new IllegalStateException("认证主体类型异常，无法获取用户 ID");
    }
}
