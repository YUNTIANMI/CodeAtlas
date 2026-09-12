package com.codeatlas.project.controller;

import com.codeatlas.auth.AuthUser;
import com.codeatlas.common.Result;
import com.codeatlas.project.dto.CreateProjectRequest;
import com.codeatlas.project.dto.ProjectVO;
import com.codeatlas.project.dto.UpdateProjectRequest;
import com.codeatlas.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目接口。
 *
 * <pre>
 * POST   /api/v1/projects      创建项目
 * GET    /api/v1/projects      我参与的项目列表
 * GET    /api/v1/projects/{id} 项目详情
 * PUT    /api/v1/projects/{id} 修改项目
 * DELETE /api/v1/projects/{id} 删除项目（OWNER）
 * </pre>
 *
 * <p>Controller 只做参数校验与转发，权限判断全部在 Service 层完成，
 * 不直接访问数据库（见 docs/development.md 第 5 节要求）。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody CreateProjectRequest request,
                                    Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return Result.success(projectService.create(userId, request));
    }

    @GetMapping
    public Result<List<ProjectVO>> list(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return Result.success(projectService.listMyProjects(userId));
    }

    @GetMapping("/{id}")
    public Result<ProjectVO> detail(@PathVariable Long id, Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return Result.success(projectService.getById(id, userId));
    }

    @PutMapping("/{id}")
    public Result<ProjectVO> update(@PathVariable Long id,
                                    @Valid @RequestBody UpdateProjectRequest request,
                                    Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return Result.success(projectService.update(id, userId, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        Long userId = resolveUserId(authentication);
        projectService.delete(id, userId);
        return Result.success();
    }

    private Long resolveUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthUser authUser) {
            return authUser.getUserId();
        }
        throw new IllegalStateException("认证主体类型异常，无法获取用户 ID");
    }
}
