package com.codeatlas.git.repository;

import com.codeatlas.git.entity.GitRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Git 仓库配置数据访问。
 */
@Repository
public interface GitRepositoryRepository extends JpaRepository<GitRepository, Long> {

    Optional<GitRepository> findByProjectId(Long projectId);
}
