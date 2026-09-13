package com.codeatlas.chat.repository;

import com.codeatlas.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话数据访问。
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByProjectIdAndUserIdOrderByUpdatedAtDesc(Long projectId, Long userId);
}
