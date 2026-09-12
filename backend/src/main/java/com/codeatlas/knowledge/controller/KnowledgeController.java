package com.codeatlas.knowledge.controller;

import com.codeatlas.auth.AuthUser;
import com.codeatlas.common.Result;
import com.codeatlas.knowledge.dto.AskAnswerVO;
import com.codeatlas.knowledge.dto.BuildResultVO;
import com.codeatlas.knowledge.dto.IndexStatusVO;
import com.codeatlas.knowledge.dto.QueryRequest;
import com.codeatlas.knowledge.dto.SearchResultVO;
import com.codeatlas.knowledge.service.KnowledgeService;
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
 * 知识库与 RAG 接口。
 *
 * <pre>
 * POST   /api/v1/projects/{projectId}/knowledge/build    构建知识库
 * GET    /api/v1/projects/{projectId}/knowledge/status   索引状态
 * POST   /api/v1/projects/{projectId}/knowledge/search   向量检索
 * POST   /api/v1/projects/{projectId}/knowledge/ask      RAG 问答
 * DELETE /api/v1/projects/{projectId}/knowledge          清空知识库
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/build")
    public Result<BuildResultVO> build(@PathVariable Long projectId,
                                       Authentication authentication) {
        return Result.success(knowledgeService.buildIndex(projectId, currentUserId(authentication)));
    }

    @GetMapping("/status")
    public Result<IndexStatusVO> status(@PathVariable Long projectId,
                                        Authentication authentication) {
        return Result.success(knowledgeService.status(projectId, currentUserId(authentication)));
    }

    @PostMapping("/search")
    public Result<List<SearchResultVO>> search(@PathVariable Long projectId,
                                               @Valid @RequestBody QueryRequest request,
                                               Authentication authentication) {
        return Result.success(knowledgeService.search(
                projectId, currentUserId(authentication), request.getQuery(), request.getTopK()));
    }

    @PostMapping("/ask")
    public Result<AskAnswerVO> ask(@PathVariable Long projectId,
                                   @Valid @RequestBody QueryRequest request,
                                   Authentication authentication) {
        return Result.success(knowledgeService.ask(
                projectId, currentUserId(authentication), request.getQuery(), request.getTopK()));
    }

    @DeleteMapping
    public Result<Void> clear(@PathVariable Long projectId,
                              Authentication authentication) {
        knowledgeService.clear(projectId, currentUserId(authentication));
        return Result.success();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.getUserId();
        }
        throw new IllegalStateException("认证主体类型异常，无法获取用户 ID");
    }
}
