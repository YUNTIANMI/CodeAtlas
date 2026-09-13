package com.codeatlas.ai.repository;

import com.codeatlas.ai.entity.AiExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 执行日志数据访问。
 */
@Repository
public interface AiExecutionLogRepository extends JpaRepository<AiExecutionLog, Long> {

    List<AiExecutionLog> findTop50ByProjectIdOrderByIdDesc(Long projectId);
}
