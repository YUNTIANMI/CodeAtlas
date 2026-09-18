package com.codeatlas.git.repository;

import com.codeatlas.git.entity.GitCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Git 提交数据访问。
 */
@Repository
public interface GitCommitRepository extends JpaRepository<GitCommit, Long> {

    List<GitCommit> findByRepoIdOrderByCommittedAtDesc(Long repoId);

    List<GitCommit> findByRepoIdAndAnalyzedFalseOrderByCommittedAtDesc(Long repoId);

    /**
     * 判断指定仓库下是否已存在该提交。
     *
     * <p>判重必须带上 repoId。多个项目可以导入同一个仓库，提交哈希本身并不唯一：
     * 若只按 commit_hash 全局判重，后导入的项目会把全部提交都当成「已存在」而跳过，
     * 导致该项目提交列表为空或长期不全。
     */
    boolean existsByRepoIdAndCommitHash(Long repoId, String commitHash);
}
