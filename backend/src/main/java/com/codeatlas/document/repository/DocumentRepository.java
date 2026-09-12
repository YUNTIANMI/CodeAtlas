package com.codeatlas.document.repository;

import com.codeatlas.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文档数据访问。
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(Long projectId);

    @SuppressWarnings("SpringDataMethodInconsistencyInspection")
    Optional<Document> findByIdAndDeletedFalse(Long id);

    /** 按名称关键字检索（基础检索，语义检索在 Phase 5 的 RAG 中实现）。 */
    List<Document> findByProjectIdAndDeletedFalseAndNameContainingIgnoreCase(
            Long projectId, String keyword);
}
