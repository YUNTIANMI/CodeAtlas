package com.codeatlas.git.repository;

import com.codeatlas.git.entity.GitCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Git 提交数据访问。
 */
@Repository
public interface GitCommitRepository extends JpaRepository<GitCommit, Long> {

    List<GitCommit> findByRepoIdOrderByCommittedAtDesc(Long repoId);

    Optional<GitCommit> findByCommitHash(String commitHash);

    List<GitCommit> findByRepoIdAndAnalyzedFalseOrderByCommittedAtDesc(Long repoId);

    boolean existsByCommitHash(String commitHash);
}
