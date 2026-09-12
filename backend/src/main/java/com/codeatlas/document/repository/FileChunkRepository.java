package com.codeatlas.document.repository;

import com.codeatlas.document.entity.FileChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文件切分块数据访问。
 */
@Repository
public interface FileChunkRepository extends JpaRepository<FileChunk, Long> {

    List<FileChunk> findBySourceTypeAndSourceIdOrderByChunkIndexAsc(
            FileChunk.SourceType sourceType, Long sourceId);

    List<FileChunk> findByProjectIdAndIndexedFalse(Long projectId);

    List<FileChunk> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    int deleteBySourceTypeAndSourceId(FileChunk.SourceType sourceType, Long sourceId);
}
