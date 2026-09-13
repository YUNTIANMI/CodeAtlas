package com.codeatlas.review.controller;

import com.codeatlas.auth.AuthUser;
import com.codeatlas.common.Result;
import com.codeatlas.review.dto.ReviewRequest;
import com.codeatlas.review.dto.ReviewResultVO;
import com.codeatlas.review.service.CodeReviewService;
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
 * AI Code Review 接口。
 *
 * <pre>
 * POST /api/v1/projects/{projectId}/review   提交审查
 * GET  /api/v1/projects/{projectId}/reviews  审查结果列表（可按 severity 过滤）
 * GET  /api/v1/reviews/{id}                  审查结果详情
 * </pre>
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final CodeReviewService codeReviewService;

    public ReviewController(CodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }

    @PostMapping("/projects/{projectId}/review")
    public Result<List<ReviewResultVO>> review(@PathVariable Long projectId,
                                               @Valid @RequestBody ReviewRequest request,
                                               Authentication authentication) {
        return Result.success(codeReviewService.review(projectId, currentUserId(authentication),
                request.getSourceType(), request.getSourceRef(), request.getContent()));
    }

    @GetMapping("/projects/{projectId}/reviews")
    public Result<List<ReviewResultVO>> list(@PathVariable Long projectId,
                                             @RequestParam(value = "severity", required = false) String severity,
                                             Authentication authentication) {
        return Result.success(codeReviewService.list(projectId, currentUserId(authentication), severity));
    }

    @GetMapping("/reviews/{id}")
    public Result<ReviewResultVO> detail(@PathVariable Long id,
                                         Authentication authentication) {
        return Result.success(codeReviewService.getById(id, currentUserId(authentication)));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.getUserId();
        }
        throw new IllegalStateException("认证主体类型异常，无法获取用户 ID");
    }
}
