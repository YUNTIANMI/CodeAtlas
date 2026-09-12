package com.codeatlas.code.repository;

import com.codeatlas.code.entity.CodeFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 代码文件数据访问。
 */
@Repository
public interface CodeFileRepository extends JpaRepository<CodeFile, Long> {

    List<CodeFile> findByProjectIdOrderByFilePathAsc(Long projectId);

    List<CodeFile> findByProjectIdAndLanguageOrderByFilePathAsc(Long projectId, String language);

    Optional<CodeFile> findByProjectIdAndFilePath(Long projectId, String filePath);

    /** 按文件路径关键字检索。 */
    List<CodeFile> findByProjectIdAndFilePathContainingIgnoreCase(Long projectId, String keyword);
}
