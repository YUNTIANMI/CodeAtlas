package com.codeatlas.git.dto;

import com.codeatlas.git.entity.GitRepository;

import java.time.LocalDateTime;

/**
 * Git 仓库视图对象（不含任何凭证信息）。
 */
public class GitRepositoryVO {

    private Long id;

    private Long projectId;

    private String repoUrl;

    private String provider;

    private String fullName;

    private String defaultBranch;

    private LocalDateTime lastSyncedAt;

    private String syncStatus;

    public static GitRepositoryVO from(GitRepository repository) {
        GitRepositoryVO vo = new GitRepositoryVO();
        vo.setId(repository.getId());
        vo.setProjectId(repository.getProjectId());
        vo.setRepoUrl(repository.getRepoUrl());
        vo.setProvider(repository.getProvider());
        vo.setFullName(repository.getFullName());
        vo.setDefaultBranch(repository.getDefaultBranch());
        vo.setLastSyncedAt(repository.getLastSyncedAt());
        vo.setSyncStatus(repository.getSyncStatus());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }
}
