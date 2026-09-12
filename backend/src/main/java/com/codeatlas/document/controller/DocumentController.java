package com.codeatlas.document.controller;

import com.codeatlas.auth.AuthUser;
import com.codeatlas.common.Result;
import com.codeatlas.document.dto.DocumentDetailVO;
import com.codeatlas.document.dto.DocumentVO;
import com.codeatlas.document.service.DocumentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档接口。
 *
 * <pre>
 * POST   /api/v1/projects/{projectId}/documents           上传文档
 * GET    /api/v1/projects/{projectId}/documents           文档列表
 * GET    /api/v1/projects/{projectId}/documents/search    按名称检索
 * GET    /api/v1/documents/{documentId}                   文档详情
 * DELETE /api/v1/documents/{documentId}                   删除文档
 * </pre>
 *
 * <p>支持 md / txt / pdf，见 docs/development.md 第 6 节。
 */
@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/projects/{projectId}/documents")
    public Result<DocumentVO> upload(@PathVariable Long projectId,
                                     @RequestParam("file") MultipartFile file,
                                     Authentication authentication) {
        return Result.success(documentService.upload(projectId, currentUserId(authentication), file));
    }

    @GetMapping("/projects/{projectId}/documents")
    public Result<List<DocumentVO>> list(@PathVariable Long projectId,
                                         Authentication authentication) {
        return Result.success(documentService.list(projectId, currentUserId(authentication)));
    }

    @GetMapping("/projects/{projectId}/documents/search")
    public Result<List<DocumentVO>> search(@PathVariable Long projectId,
                                           @RequestParam String keyword,
                                           Authentication authentication) {
        return Result.success(documentService.search(projectId, currentUserId(authentication), keyword));
    }

    @GetMapping("/documents/{documentId}")
    public Result<DocumentDetailVO> detail(@PathVariable Long documentId,
                                           Authentication authentication) {
        return Result.success(documentService.getById(documentId, currentUserId(authentication)));
    }

    @DeleteMapping("/documents/{documentId}")
    public Result<Void> delete(@PathVariable Long documentId,
                               Authentication authentication) {
        documentService.delete(documentId, currentUserId(authentication));
        return Result.success();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.getUserId();
        }
        throw new IllegalStateException("认证主体类型异常，无法获取用户 ID");
    }
}
