package com.codeatlas.project.dto;

import com.codeatlas.project.entity.ProjectMember;
import com.codeatlas.project.entity.ProjectRole;

import java.time.LocalDateTime;

/**
 * 项目成员视图对象。
 */
public class ProjectMemberVO {

    private Long id;

    private Long userId;

    private String username;

    private ProjectRole role;

    private LocalDateTime joinedAt;

    public static ProjectMemberVO from(ProjectMember member, String username) {
        ProjectMemberVO vo = new ProjectMemberVO();
        vo.setId(member.getId());
        vo.setUserId(member.getUserId());
        vo.setUsername(username);
        vo.setRole(member.getRole());
        vo.setJoinedAt(member.getJoinedAt());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public ProjectRole getRole() {
        return role;
    }

    public void setRole(ProjectRole role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
