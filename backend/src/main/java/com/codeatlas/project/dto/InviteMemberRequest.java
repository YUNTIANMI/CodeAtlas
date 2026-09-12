package com.codeatlas.project.dto;

import com.codeatlas.project.entity.ProjectRole;
import jakarta.validation.constraints.NotNull;

/**
 * 邀请成员请求。
 */
public class InviteMemberRequest {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    @NotNull(message = "角色不能为空")
    private ProjectRole role;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public ProjectRole getRole() {
        return role;
    }

    public void setRole(ProjectRole role) {
        this.role = role;
    }
}
