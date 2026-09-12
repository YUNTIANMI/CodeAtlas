package com.codeatlas.project.controller;

import com.codeatlas.auth.AuthUser;
import com.codeatlas.common.Result;
import com.codeatlas.project.dto.InviteMemberRequest;
import com.codeatlas.project.dto.ProjectMemberVO;
import com.codeatlas.project.dto.UpdateMemberRoleRequest;
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
 * 项目成员接口。
 *
 * <pre>
 * GET    /api/v1/projects/{id}/members              成员列表
 * POST   /api/v1/projects/{id}/members              邀请成员
 * PUT    /api/v1/projects/{id}/members/{userId}     修改成员角色
 * DELETE /api/v1/projects/{id}/members/{userId}     移除成员
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectService projectService;

    public ProjectMemberController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Result<List<ProjectMemberVO>> list(@PathVariable Long projectId,
                                              Authentication authentication) {
        Long operatorId = resolveUserId(authentication);
        return Result.success(projectService.listMembers(projectId, operatorId));
    }

    @PostMapping
    public Result<ProjectMemberVO> invite(@PathVariable Long projectId,
                                          @Valid @RequestBody InviteMemberRequest request,
                                          Authentication authentication) {
        Long operatorId = resolveUserId(authentication);
        return Result.success(projectService.inviteMember(
                projectId, operatorId, request.getUserId(), request.getRole()));
    }

    @PutMapping("/{userId}")
    public Result<ProjectMemberVO> updateRole(@PathVariable Long projectId,
                                              @PathVariable Long userId,
                                              @Valid @RequestBody UpdateMemberRoleRequest request,
                                              Authentication authentication) {
        Long operatorId = resolveUserId(authentication);
        return Result.success(projectService.updateMemberRole(
                projectId, operatorId, userId, request.getRole()));
    }

    @DeleteMapping("/{userId}")
    public Result<Void> remove(@PathVariable Long projectId,
                               @PathVariable Long userId,
                               Authentication authentication) {
        Long operatorId = resolveUserId(authentication);
        projectService.removeMember(projectId, operatorId, userId);
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
