package com.codeatlas.chat.repository;

import com.codeatlas.chat.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息数据访问。
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    /** 取最近若干条历史，用于构造多轮上下文。 */
    List<Message> findTop10ByConversationIdOrderByIdDesc(Long conversationId);
}
