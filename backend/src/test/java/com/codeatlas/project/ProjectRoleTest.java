package com.codeatlas.project;

import com.codeatlas.project.entity.ProjectRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 项目角色权限矩阵测试，对应 docs/database.md 4.4 节。
 */
class ProjectRoleTest {

    @Test
    @DisplayName("管理权限：仅 OWNER 与 ADMIN")
    void canManage() {
        assertTrue(ProjectRole.OWNER.canManage());
        assertTrue(ProjectRole.ADMIN.canManage());
        assertFalse(ProjectRole.MEMBER.canManage());
        assertFalse(ProjectRole.VIEWER.canManage());
    }

    @Test
    @DisplayName("写入权限：OWNER / ADMIN / MEMBER，VIEWER 只读")
    void canWrite() {
        assertTrue(ProjectRole.OWNER.canWrite());
        assertTrue(ProjectRole.ADMIN.canWrite());
        assertTrue(ProjectRole.MEMBER.canWrite());
        assertFalse(ProjectRole.VIEWER.canWrite());
    }
}
