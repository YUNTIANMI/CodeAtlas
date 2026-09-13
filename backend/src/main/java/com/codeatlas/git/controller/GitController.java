package com.codeatlas.git.controller;

import com.codeatlas.auth.AuthUser;
import com.codeatlas.common.Result;
import com.codeatlas.git.dto.GitCommitVO;
import com.codeatlas.git.dto.GitRepositoryVO;
import com.codeatlas.git.dto.ImportRepoRequest;
import com.codeatlas.git.dto.SyncResultVO;
import com.codeatlas.git.service.GitService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Git 分析接口（只读）。
 *
 * <pre>
 * POST /api/v1/projects/{projectId}/git                    导入仓库
 * POST /api/v1/projects/{projectId}/git/sync               同步提交
 * GET  /api/v1/projects/{projectId}/git/commits            提交列表
 * GET  /api/v1/git/commits/{commitId}                      提交详情
 * GET  /api/v1/git/commits/{commitId}/summary              生成 AI 摘要
 * </pre>
 */
@RestController
@RequestMapping("/api/v1")
public class GitController {

    private final GitService gitService;

    public GitController(GitService gitService) {
        this.gitService = gitService;
    }

    @PostMapping("/projects/{projectId}/git")
    public Result<GitRepositoryVO> importRepository(@PathVariable Long projectId,
                                                    @Valid @RequestBody ImportRepoRequest request,
                                                    Authentication authentication) {
        return Result.success(gitService.importRepository(projectId, currentUserId(authentication),
                request.getRepoUrl(), request.getAccessTokenRef()));
    }

    @PostMapping("/projects/{projectId}/git/sync")
    public Result<SyncResultVO> sync(@PathVariable Long projectId,
                                     @RequestParam(value = "limit", required = false) Integer limit,
                                     Authentication authentication) {
        return Result.success(gitService.syncCommits(projectId, currentUserId(authentication), limit));
    }

    @GetMapping("/projects/{projectId}/git/commits")
    public Result<List<GitCommitVO>> commits(@PathVariable Long projectId,
                                             Authentication authentication) {
        return Result.success(gitService.listCommits(projectId, currentUserId(authentication)));
    }

    @GetMapping("/git/commits/{commitId}")
    public Result<GitCommitVO> commitDetail(@PathVariable Long commitId,
                                            Authentication authentication) {
        Object principal = authentication.getPrincipal();
        Long userId = ((AuthUser) principal).getUserId();
        // 详情需要项目上下文，此处通过提交所属仓库反查项目
        return Result.success(gitService.getCommitByCommitId(commitId, userId));
    }

    @GetMapping("/git/commits/{commitId}/summary")
    public Result<GitCommitVO> summary(@PathVariable Long commitId,
                                       Authentication authentication) {
        Object principal = authentication.getPrincipal();
        Long userId = ((AuthUser) principal).getUserId();
        return Result.success(gitService.getSummaryByCommitId(commitId, userId));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.getUserId();
        }
        throw new IllegalStateException("认证主体类型异常，无法获取用户 ID");
    }
}
