package com.codeatlas.git.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Git 提交记录，对应 git_commits 表。
 *
 * <p>字段设计详见 docs/database.md 4.8 节。
 */
@Entity
@Table(name = "git_commits", uniqueConstraints = @UniqueConstraint(
        name = "uk_git_commits_repo_hash", columnNames = {"repo_id", "commit_hash"}))
public class GitCommit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    /**
     * 提交哈希。唯一性由 (repo_id, commit_hash) 联合约束保证，不能只对哈希做全局唯一：
     * 同一仓库可能被多个项目导入，跨仓库也可能出现重复哈希（fork / cherry-pick）。
     */
    @Column(name = "commit_hash", nullable = false, length = 64)
    private String commitHash;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "author_name", length = 100)
    private String authorName;

    @Column(name = "author_email", length = 100)
    private String authorEmail;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    private Integer additions;

    private Integer deletions;

    /** 变更内容（patch），用于 AI 分析。 */
    @Column(name = "diff_content", columnDefinition = "LONGTEXT")
    private String diffContent;

    /** AI 生成的提交摘要（Phase 8 新增字段）。 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private Boolean analyzed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.analyzed == null) {
            this.analyzed = false;
        }
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

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
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

    public String getDiffContent() {
        return diffContent;
    }

    public void setDiffContent(String diffContent) {
        this.diffContent = diffContent;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
