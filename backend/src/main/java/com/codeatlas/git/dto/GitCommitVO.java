package com.codeatlas.git.dto;

import com.codeatlas.git.entity.GitCommit;

import java.time.LocalDateTime;

/**
 * Git 提交视图对象：diff 内容默认不下发，避免响应过大。
 */
public class GitCommitVO {

    private Long id;

    private Long repoId;

    private String commitHash;

    private String message;

    private String authorName;

    private LocalDateTime committedAt;

    private Integer additions;

    private Integer deletions;

    private String summary;

    private Boolean analyzed;

    public static GitCommitVO from(GitCommit commit) {
        GitCommitVO vo = new GitCommitVO();
        vo.setId(commit.getId());
        vo.setRepoId(commit.getRepoId());
        vo.setCommitHash(commit.getCommitHash());
        vo.setMessage(commit.getMessage());
        vo.setAuthorName(commit.getAuthorName());
        vo.setCommittedAt(commit.getCommittedAt());
        vo.setAdditions(commit.getAdditions());
        vo.setDeletions(commit.getDeletions());
        vo.setSummary(commit.getSummary());
        vo.setAnalyzed(commit.getAnalyzed());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRepoId() {
        return repoId;
    }

    public void setRepoId(Long repoId) {
        this.repoId = repoId;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public LocalDateTime getCommittedAt() {
        return committedAt;
    }

    public void setCommittedAt(LocalDateTime committedAt) {
        this.committedAt = committedAt;
    }

    public Integer getAdditions() {
        return additions;
    }

    public void setAdditions(Integer additions) {
        this.additions = additions;
    }

    public Integer getDeletions() {
        return deletions;
    }

    public void setDeletions(Integer deletions) {
        this.deletions = deletions;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Boolean getAnalyzed() {
        return analyzed;
    }

    public void setAnalyzed(Boolean analyzed) {
        this.analyzed = analyzed;
    }
}
