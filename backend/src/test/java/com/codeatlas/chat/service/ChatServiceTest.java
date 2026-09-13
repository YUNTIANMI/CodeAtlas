package com.codeatlas.chat.service;

import com.codeatlas.ai.entity.AiExecutionLog;
import com.codeatlas.ai.provider.ChatProvider;
import com.codeatlas.ai.repository.AiExecutionLogRepository;
import com.codeatlas.chat.dto.ConversationVO;
import com.codeatlas.chat.dto.MessageVO;
import com.codeatlas.chat.entity.Citation;
import com.codeatlas.chat.entity.Conversation;
import com.codeatlas.chat.entity.Message;
import com.codeatlas.chat.repository.CitationRepository;
import com.codeatlas.chat.repository.ConversationRepository;
import com.codeatlas.chat.repository.MessageRepository;
import com.codeatlas.code.entity.CodeFile;
import com.codeatlas.code.repository.CodeFileRepository;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.document.entity.Document;
import com.codeatlas.document.repository.DocumentRepository;
import com.codeatlas.knowledge.dto.SearchResultVO;
import com.codeatlas.knowledge.service.KnowledgeService;
import com.codeatlas.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 项目问答服务测试：会话、多轮上下文、引用来源与异常处理。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private CitationRepository citationRepository;

    @Mock
    private AiExecutionLogRepository executionLogRepository;

    @Mock
    private ProjectPermissionService permissionService;

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private ChatProvider chatProvider;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private CodeFileRepository codeFileRepository;

    @InjectMocks
    private ChatService chatService;

    private static final Long PROJECT_ID = 10L;

    private static final Long USER_ID = 1L;

    private static final Long CONVERSATION_ID = 5L;

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversation = new Conversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setProjectId(PROJECT_ID);
        conversation.setUserId(USER_ID);
        conversation.setTitle("历史会话");
        // 多数用例无历史消息，统一返回空列表（严格模式下需 lenient）
        lenient().when(messageRepository.findTop10ByConversationIdOrderByIdDesc(anyLong()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("首次提问：自动创建会话并保存问答与引用")
    void askCreatesConversation() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation c = invocation.getArgument(0);
            c.setId(CONVERSATION_ID);
            return c;
        });
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(100L);
            return m;
        });
        when(knowledgeService.search(anyLong(), anyLong(), anyString(), any()))
                .thenReturn(List.of(new SearchResultVO(1L, "登录流程内容", 0.9,
                        "DOCUMENT", 2L, 0)));
        when(documentRepository.findById(2L)).thenAnswer(invocation -> {
            Document doc = new Document();
            doc.setId(2L);
            doc.setName("登录流程说明.md");
            return Optional.of(doc);
        });
        when(chatProvider.chat(anyString(), anyList(), anyString()))
                .thenReturn("登录由三个模块组成。");
        when(chatProvider.modelName()).thenReturn("deepseek:deepseek-chat");

        MessageVO result = chatService.ask(PROJECT_ID, USER_ID, null, "登录流程是什么", 5);

        assertNotNull(result);
        assertEquals("登录由三个模块组成。", result.getContent());
        assertEquals("ASSISTANT", result.getRole());
        assertEquals(1, result.getCitations().size());
        // 引用应解析为具体文件名，而不是只给一个 ID
        assertEquals("登录流程说明.md", result.getCitations().get(0).fileName());
        // 创建会话一次，结束时更新会话时间一次
        verify(conversationRepository, atLeastOnce()).save(any(Conversation.class));
        verify(citationRepository).save(any(Citation.class));
        verify(executionLogRepository).save(any(AiExecutionLog.class));
    }

    @Test
    @DisplayName("提问失败：内容为空")
    void askWithBlankQuestion() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.ask(PROJECT_ID, USER_ID, null, "   ", 5));
        assertEquals(ErrorCode.MESSAGE_EMPTY, ex.getErrorCode());
        verify(knowledgeService, never()).search(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("提问失败：非项目成员")
    void askWithoutPermission() {
        doThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER))
                .when(permissionService).requireMember(PROJECT_ID, 999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.ask(PROJECT_ID, 999L, null, "问题", 5));
        assertEquals(ErrorCode.NOT_PROJECT_MEMBER, ex.getErrorCode());
    }

    @Test
    @DisplayName("多轮追问：历史对话会传入模型")
    void askWithHistory() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if (m.getId() == null) {
                m.setId(101L);
            }
            return m;
        });
        // 数据库按 id 倒序返回，服务层会再按 id 正序还原对话顺序
        when(messageRepository.findTop10ByConversationIdOrderByIdDesc(CONVERSATION_ID))
                .thenReturn(List.of(previousMessage(2L, "ASSISTANT", "登录由 UserController 处理"),
                        previousMessage(1L, "USER", "登录流程是什么")));
        when(knowledgeService.search(anyLong(), anyLong(), anyString(), any()))
                .thenReturn(List.of());
        when(chatProvider.chat(anyString(), anyList(), anyString())).thenReturn("密码使用 BCrypt。");
        when(chatProvider.modelName()).thenReturn("deepseek:deepseek-chat");

        chatService.ask(PROJECT_ID, USER_ID, CONVERSATION_ID, "那密码呢", 5);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String[]>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatProvider).chat(anyString(), captor.capture(), anyString());

        List<String[]> history = captor.getValue();
        assertEquals(2, history.size(), "应携带两轮历史");
        assertEquals("USER", history.get(0)[0]);
        assertEquals("登录流程是什么", history.get(0)[1]);
        assertEquals("ASSISTANT", history.get(1)[0]);
    }

    @Test
    @DisplayName("检索无结果：仍给出明确答复，不编造")
    void askWithoutContext() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(102L);
            return m;
        });
        when(knowledgeService.search(anyLong(), anyLong(), anyString(), any())).thenReturn(List.of());
        when(chatProvider.chat(anyString(), anyList(), anyString()))
                .thenReturn("在项目资料中未找到相关依据。");
        when(chatProvider.modelName()).thenReturn("deepseek:deepseek-chat");

        MessageVO result = chatService.ask(PROJECT_ID, USER_ID, CONVERSATION_ID, "不相关的问题", 5);

        assertTrue(result.getCitations().isEmpty());
        verify(citationRepository, never()).save(any(Citation.class));
    }

    @Test
    @DisplayName("代码来源的引用解析为文件路径")
    void citationResolvesCodePath() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(103L);
            return m;
        });
        when(knowledgeService.search(anyLong(), anyLong(), anyString(), any()))
                .thenReturn(List.of(new SearchResultVO(9L, "public class Demo", 0.8,
                        "CODE", 7L, 0)));
        when(codeFileRepository.findById(7L)).thenAnswer(invocation -> {
            CodeFile file = new CodeFile();
            file.setId(7L);
            file.setFilePath("src/main/java/Demo.java");
            return Optional.of(file);
        });
        when(chatProvider.chat(anyString(), anyList(), anyString())).thenReturn("参见 Demo 类。");
        when(chatProvider.modelName()).thenReturn("deepseek:deepseek-chat");

        MessageVO result = chatService.ask(PROJECT_ID, USER_ID, CONVERSATION_ID, "Demo 类是做什么的", 5);

        assertEquals("src/main/java/Demo.java", result.getCitations().get(0).fileName());
        assertEquals("CODE", result.getCitations().get(0).sourceType());
    }

    @Test
    @DisplayName("会话不属于该项目时拒绝访问")
    void askWithConversationFromOtherProject() {
        Conversation other = new Conversation();
        other.setId(99L);
        other.setProjectId(777L);
        when(conversationRepository.findById(99L)).thenReturn(Optional.of(other));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.ask(PROJECT_ID, USER_ID, 99L, "问题", 5));
        assertEquals(ErrorCode.CONVERSATION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("消息历史：携带每条回答的引用")
    void listMessages() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByIdAsc(CONVERSATION_ID))
                .thenReturn(List.of(previousMessage("USER", "问题"),
                        previousMessage("ASSISTANT", "回答")));
        when(citationRepository.findByMessageIdOrderByScoreDesc(anyLong()))
                .thenReturn(List.of(buildCitation("登录流程说明.md")));

        List<MessageVO> messages = chatService.listMessages(CONVERSATION_ID, USER_ID);

        assertEquals(2, messages.size());
        assertEquals("问题", messages.get(0).getContent());
        assertEquals(1, messages.get(1).getCitations().size());
        assertEquals("登录流程说明.md", messages.get(1).getCitations().get(0).fileName());
    }

    @Test
    @DisplayName("会话列表：只返回当前用户在该项目的会话")
    void listConversations() {
        when(conversationRepository.findByProjectIdAndUserIdOrderByUpdatedAtDesc(PROJECT_ID, USER_ID))
                .thenReturn(List.of(conversation));

        List<ConversationVO> result = chatService.listConversations(PROJECT_ID, USER_ID);

        assertEquals(1, result.size());
        assertEquals("历史会话", result.get(0).getTitle());
        verify(permissionService).requireMember(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("删除会话：级联删除消息与引用")
    void deleteConversation() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByIdAsc(CONVERSATION_ID))
                .thenReturn(List.of(previousMessage("USER", "问题")));
        when(citationRepository.findByMessageIdOrderByScoreDesc(anyLong()))
                .thenReturn(List.of(buildCitation("x.md")));

        chatService.deleteConversation(CONVERSATION_ID, USER_ID);

        verify(citationRepository).delete(any(Citation.class));
        verify(messageRepository).deleteAll(any());
        verify(conversationRepository).delete(conversation);
    }

    private Message previousMessage(String role, String content) {
        return previousMessage(1L, role, content);
    }

    private Message previousMessage(Long id, String role, String content) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(CONVERSATION_ID);
        message.setRole(Message.Role.valueOf(role));
        message.setContent(content);
        return message;
    }

    private Citation buildCitation(String fileName) {
        Citation citation = new Citation();
        citation.setId(1L);
        citation.setMessageId(1L);
        citation.setSourceType("DOCUMENT");
        citation.setSourceId(2L);
        citation.setFileName(fileName);
        citation.setScore(0.9);
        return citation;
    }
}
