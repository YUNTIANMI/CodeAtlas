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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI 项目问答服务。
 *
 * <p>在 Phase 5 的检索能力之上，补充三件事：
 * 会话持久化、多轮上下文、可溯源的文件名引用。
 *
 * <p>核心原则（docs/development.md 第 8 节）：
 * 回答必须尽量基于项目知识，而不是单纯依赖模型记忆。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT = """
            你是一个软件项目分析助手，请只根据提供的项目资料回答问题。

            规则：
            1. 如果项目资料中没有相关信息，请明确回答"在项目资料中未找到相关依据"，不要凭自己的知识猜测。
            2. 可以结合上文对话理解用户的追问，但结论仍必须以项目资料为准。
            3. 回答时尽量指出涉及的文件名。
            4. 使用中文，简洁准确。
            """;

    private static final int HISTORY_LIMIT = 10;

    private final ConversationRepository conversationRepository;

    private final MessageRepository messageRepository;

    private final CitationRepository citationRepository;

    private final AiExecutionLogRepository executionLogRepository;

    private final ProjectPermissionService permissionService;

    private final KnowledgeService knowledgeService;

    private final ChatProvider chatProvider;

    private final DocumentRepository documentRepository;

    private final CodeFileRepository codeFileRepository;

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       CitationRepository citationRepository,
                       AiExecutionLogRepository executionLogRepository,
                       ProjectPermissionService permissionService,
                       KnowledgeService knowledgeService,
                       ChatProvider chatProvider,
                       DocumentRepository documentRepository,
                       CodeFileRepository codeFileRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.citationRepository = citationRepository;
        this.executionLogRepository = executionLogRepository;
        this.permissionService = permissionService;
        this.knowledgeService = knowledgeService;
        this.chatProvider = chatProvider;
        this.documentRepository = documentRepository;
        this.codeFileRepository = codeFileRepository;
    }

    /** 我的会话列表。 */
    @Transactional(readOnly = true)
    public List<ConversationVO> listConversations(Long projectId, Long userId) {
        permissionService.requireMember(projectId, userId);
        return conversationRepository
                .findByProjectIdAndUserIdOrderByUpdatedAtDesc(projectId, userId)
                .stream()
                .map(ConversationVO::from)
                .toList();
    }

    /** 会话消息历史（含每条回答的引用来源）。 */
    @Transactional(readOnly = true)
    public List<MessageVO> listMessages(Long conversationId, Long userId) {
        Conversation conversation = requireConversation(conversationId);
        permissionService.requireMember(conversation.getProjectId(), userId);

        List<Message> messages = messageRepository.findByConversationIdOrderByIdAsc(conversationId);
        List<MessageVO> result = new ArrayList<>();
        for (Message message : messages) {
            List<MessageVO.CitationItem> citations = citationRepository
                    .findByMessageIdOrderByScoreDesc(message.getId())
                    .stream()
                    .map(c -> new MessageVO.CitationItem(c.getSourceType(), c.getSourceId(),
                            c.getFileName(), c.getChunkIndex(), c.getScore(), c.getSnippet()))
                    .toList();
            result.add(MessageVO.from(message, citations));
        }
        return result;
    }

    /**
     * 发起提问：检索项目知识 → 结合历史上下文 → 生成回答 → 持久化消息与引用。
     *
     * @param conversationId 为空时自动创建新会话
     */
    @Transactional
    public MessageVO ask(Long projectId, Long userId, Long conversationId, String question,
                         Integer topK) {
        permissionService.requireMember(projectId, userId);
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.MESSAGE_EMPTY);
        }

        Conversation conversation = conversationId == null
                ? createConversation(projectId, userId, question)
                : requireConversation(conversationId);
        if (!conversation.getProjectId().equals(projectId)) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        // 1. 保存用户消息
        Message userMessage = new Message();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(Message.Role.USER);
        userMessage.setContent(question.trim());
        messageRepository.save(userMessage);

        long start = System.currentTimeMillis();
        boolean success = true;
        String error = null;
        String answer;
        List<SearchResultVO> contexts = List.of();

        try {
            // 2. 检索项目资料
            contexts = knowledgeService.search(projectId, userId, question, topK);

            // 3. 组装历史上下文
            List<String[]> history = buildHistory(conversation.getId(), userMessage.getId());

            // 4. 生成回答
            String prompt = contexts.isEmpty()
                    ? "（本次未检索到项目资料，请直接说明未找到依据）\n\n问题：" + question
                    : buildContextPrompt(contexts, question);

            answer = chatProvider.chat(SYSTEM_PROMPT, history, prompt);
        } catch (BusinessException ex) {
            success = false;
            error = ex.getMessage();
            throw ex;
        } catch (Exception ex) {
            success = false;
            error = ex.getMessage();
            log.error("AI 问答失败 | projectId={} | conversationId={}", projectId, conversation.getId(), ex);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务调用失败");
        } finally {
            recordExecution(projectId, userId, contexts, question,
                    (int) (System.currentTimeMillis() - start), success, error);
        }

        // 5. 保存助手消息
        Message assistantMessage = new Message();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole(Message.Role.ASSISTANT);
        assistantMessage.setContent(answer);
        assistantMessage.setModel(chatProvider.modelName());
        Message saved = messageRepository.save(assistantMessage);

        // 6. 保存引用来源（解析为具体文件名）
        List<MessageVO.CitationItem> citationItems = new ArrayList<>();
        for (SearchResultVO context : contexts) {
            Citation citation = new Citation();
            citation.setMessageId(saved.getId());
            citation.setSourceType(context.getSourceType());
            citation.setSourceId(context.getSourceId());
            citation.setFileName(resolveFileName(context.getSourceType(), context.getSourceId()));
            citation.setChunkIndex(context.getChunkIndex());
            citation.setScore(context.getScore());
            citation.setSnippet(snippet(context.getContent()));
            citationRepository.save(citation);

            citationItems.add(new MessageVO.CitationItem(citation.getSourceType(),
                    citation.getSourceId(), citation.getFileName(), citation.getChunkIndex(),
                    citation.getScore(), citation.getSnippet()));
        }

        // 7. 更新会话时间
        conversationRepository.save(conversation);

        log.info("AI 问答完成 | conversationId={} | citations={} | model={}",
                conversation.getId(), citationItems.size(), chatProvider.modelName());
        return MessageVO.from(saved, citationItems);
    }

    /** 删除会话及其消息与引用。 */
    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conversation = requireConversation(conversationId);
        permissionService.requireMember(conversation.getProjectId(), userId);

        List<Message> messages = messageRepository.findByConversationIdOrderByIdAsc(conversationId);
        for (Message message : messages) {
            citationRepository.findByMessageIdOrderByScoreDesc(message.getId())
                    .forEach(citationRepository::delete);
        }
        messageRepository.deleteAll(messages);
        conversationRepository.delete(conversation);
    }

    private Conversation createConversation(Long projectId, Long userId, String firstQuestion) {
        Conversation conversation = new Conversation();
        conversation.setProjectId(projectId);
        conversation.setUserId(userId);
        conversation.setTitle(buildTitle(firstQuestion));
        return conversationRepository.save(conversation);
    }

    private Conversation requireConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    /** 取最近若干轮历史（不含本次提问），按时间正序。 */
    private List<String[]> buildHistory(Long conversationId, Long currentMessageId) {
        List<Message> recent = messageRepository
                .findTop10ByConversationIdOrderByIdDesc(conversationId)
                .stream()
                .filter(m -> !m.getId().equals(currentMessageId))
                .sorted(Comparator.comparing(Message::getId))
                .toList();

        List<String[]> history = new ArrayList<>();
        for (Message message : recent) {
            history.add(new String[]{message.getRole().name(), message.getContent()});
        }
        if (history.size() > HISTORY_LIMIT) {
            history = history.subList(history.size() - HISTORY_LIMIT, history.size());
        }
        return history;
    }

    private String buildContextPrompt(List<SearchResultVO> contexts, String question) {
        StringBuilder builder = new StringBuilder("项目资料：\n\n");
        for (int i = 0; i < contexts.size(); i++) {
            SearchResultVO context = contexts.get(i);
            builder.append("[").append(i + 1).append("] 来源：")
                    .append(resolveFileName(context.getSourceType(), context.getSourceId()))
                    .append("（相关度 ").append(String.format("%.2f", context.getScore())).append("）\n")
                    .append(context.getContent()).append("\n\n");
        }
        builder.append("问题：").append(question);
        return builder.toString();
    }

    /** 将来源解析为可直接展示的文件名。 */
    private String resolveFileName(String sourceType, Long sourceId) {
        if (sourceId == null) {
            return "未知来源";
        }
        if ("DOCUMENT".equalsIgnoreCase(sourceType)) {
            return documentRepository.findById(sourceId)
                    .map(Document::getName)
                    .orElse("未知文档");
        }
        if ("CODE".equalsIgnoreCase(sourceType)) {
            return codeFileRepository.findById(sourceId)
                    .map(CodeFile::getFilePath)
                    .orElse("未知代码文件");
        }
        return "未知来源";
    }

    private void recordExecution(Long projectId, Long userId, List<SearchResultVO> contexts,
                                 String question, int elapsedMs, boolean success, String error) {
        AiExecutionLog logEntity = new AiExecutionLog();
        logEntity.setProjectId(projectId);
        logEntity.setUserId(userId);
        logEntity.setRequestType("CHAT");
        logEntity.setModel(chatProvider.modelName());
        logEntity.setRetrievedContent(question);
        logEntity.setToolsUsed(contexts.isEmpty() ? "" : "vector_search:" + contexts.size());
        logEntity.setExecutionTimeMs(elapsedMs);
        logEntity.setSuccess(success);
        logEntity.setErrorMessage(error);
        executionLogRepository.save(logEntity);
    }

    private String buildTitle(String question) {
        String clean = question.replaceAll("\\s+", " ").trim();
        return clean.length() <= 30 ? clean : clean.substring(0, 30) + "…";
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        String clean = content.replaceAll("\\s+", " ").trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 120) + "…";
    }
}
