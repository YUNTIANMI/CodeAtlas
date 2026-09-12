package com.codeatlas.project.repository;

import com.codeatlas.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 项目数据访问。
 *
 * <p>所有查询均过滤软删除标记 deleted = false。
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("SELECT p FROM Project p WHERE p.id = :id AND p.deleted = false")
    Optional<Project> findActiveById(@Param("id") Long id);

    /** 查询用户参与的全部项目（含作为成员加入的，不只是自己创建的）。 */
    @Query("SELECT p FROM Project p JOIN ProjectMember m ON p.id = m.projectId "
            + "WHERE m.userId = :userId AND p.deleted = false ORDER BY p.createdAt DESC")
    List<Project> findProjectsByMemberId(@Param("userId") Long userId);
}
