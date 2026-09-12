package com.codeatlas.code.controller;

import com.codeatlas.auth.AuthUser;
import com.codeatlas.code.dto.CodeFileDetailVO;
import com.codeatlas.code.dto.CodeFileVO;
import com.codeatlas.code.dto.StructureNode;
import com.codeatlas.code.service.CodeFileService;
import com.codeatlas.common.Result;
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
 * 代码接口。
 *
 * <pre>
 * POST   /api/v1/projects/{projectId}/code             上传代码
 * GET    /api/v1/projects/{projectId}/code             代码列表
 * GET    /api/v1/projects/{projectId}/code/structure   目录结构
 * GET    /api/v1/code/{fileId}                         代码详情
 * DELETE /api/v1/code/{fileId}                         删除代码
 * </pre>
 *
 * <p>支持 java / cpp / py / js / ts，见 docs/development.md 第 6 节。
 */
@RestController
@RequestMapping("/api/v1")
public class CodeFileController {

    private final CodeFileService codeFileService;

    public CodeFileController(CodeFileService codeFileService) {
        this.codeFileService = codeFileService;
    }

    @PostMapping("/projects/{projectId}/code")
    public Result<CodeFileVO> upload(@PathVariable Long projectId,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "path", required = false) String path,
                                     Authentication authentication) {
        return Result.success(codeFileService.upload(projectId, currentUserId(authentication), file, path));
    }

    @GetMapping("/projects/{projectId}/code")
    public Result<List<CodeFileVO>> list(@PathVariable Long projectId,
                                         @RequestParam(value = "language", required = false) String language,
                                         Authentication authentication) {
        return Result.success(codeFileService.list(projectId, currentUserId(authentication), language));
    }

    @GetMapping("/projects/{projectId}/code/structure")
    public Result<StructureNode> structure(@PathVariable Long projectId,
                                           Authentication authentication) {
        return Result.success(codeFileService.getStructure(projectId, currentUserId(authentication)));
    }

    @GetMapping("/code/{fileId}")
    public Result<CodeFileDetailVO> detail(@PathVariable Long fileId,
                                           Authentication authentication) {
        return Result.success(codeFileService.getById(fileId, currentUserId(authentication)));
    }

    @DeleteMapping("/code/{fileId}")
    public Result<Void> delete(@PathVariable Long fileId,
                               Authentication authentication) {
        codeFileService.delete(fileId, currentUserId(authentication));
        return Result.success();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.getUserId();
        }
        throw new IllegalStateException("认证主体类型异常，无法获取用户 ID");
    }
}
