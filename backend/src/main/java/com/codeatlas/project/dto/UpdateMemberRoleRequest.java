package com.codeatlas.project.dto;

import com.codeatlas.project.entity.ProjectRole;
import jakarta.validation.constraints.NotNull;

/**
 * 修改成员角色请求。
 */
public class UpdateMemberRoleRequest {

    @NotNull(message = "角色不能为空")
    private ProjectRole role;

    public ProjectRole getRole() {
        return role;
    }

    public void setRole(ProjectRole role) {
        this.role = role;
    }
}
