package com.codeatlas.project.dto;

import com.codeatlas.project.entity.Project;
import com.codeatlas.project.entity.ProjectRole;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 项目视图对象。
 *
 * <p>myRole 表示「当前请求者」在该项目中的角色，前端据此决定按钮显隐。
 */
public class ProjectVO {

    private Long id;

    private String name;

    private String description;

    private String projectType;

    private List<String> techStack;

    private Long ownerId;

    private Integer status;

    private ProjectRole myRole;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static ProjectVO from(Project project, ProjectRole myRole) {
        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setDescription(project.getDescription());
        vo.setProjectType(project.getProjectType());
        vo.setTechStack(splitTechStack(project.getTechStack()));
        vo.setOwnerId(project.getOwnerId());
        vo.setStatus(project.getStatus());
        vo.setMyRole(myRole);
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());
        return vo;
    }

    private static List<String> splitTechStack(String techStack) {
        if (techStack == null || techStack.isBlank()) {
            return List.of();
        }
        return Arrays.stream(techStack.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public List<String> getTechStack() {
        return techStack;
    }

    public void setTechStack(List<String> techStack) {
        this.techStack = techStack;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public ProjectRole getMyRole() {
        return myRole;
    }

    public void setMyRole(ProjectRole myRole) {
        this.myRole = myRole;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
