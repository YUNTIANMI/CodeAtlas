package com.codeatlas.review.repository;

import com.codeatlas.review.entity.ReviewResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 代码审查结果数据访问。
 */
@Repository
public interface ReviewResultRepository extends JpaRepository<ReviewResult, Long> {

    List<ReviewResult> findByProjectIdOrderByIdDesc(Long projectId);

    List<ReviewResult> findByProjectIdAndSeverityOrderByIdDesc(Long projectId, String severity);

    long countByProjectId(Long projectId);
}
