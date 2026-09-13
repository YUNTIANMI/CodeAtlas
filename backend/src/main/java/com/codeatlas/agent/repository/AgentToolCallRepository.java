package com.codeatlas.agent.repository;

import com.codeatlas.agent.entity.AgentToolCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 工具调用记录数据访问。
 */
@Repository
public interface AgentToolCallRepository extends JpaRepository<AgentToolCall, Long> {

    List<AgentToolCall> findTop50ByProjectIdOrderByIdDesc(Long projectId);
}
