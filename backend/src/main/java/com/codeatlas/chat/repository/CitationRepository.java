package com.codeatlas.chat.repository;

import com.codeatlas.chat.entity.Citation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 引用来源数据访问。
 */
@Repository
public interface CitationRepository extends JpaRepository<Citation, Long> {

    List<Citation> findByMessageIdOrderByScoreDesc(Long messageId);
}
