package com.codeatlas.storage;

import java.io.IOException;

/**
 * 文件存储抽象。
 *
 * <p>业务代码只依赖本接口，不直接操作具体存储，
 * 便于未来从本地存储迁移到 S3 / MinIO / OSS。
 */
public interface StorageService {

    /**
     * 保存文件。
     *
     * @param projectId      项目 ID，用于隔离目录
     * @param storedFileName 落盘文件名（由调用方生成，避免重名与路径穿越）
     * @param data           文件内容
     * @return 存储路径
     */
    String save(Long projectId, String storedFileName, byte[] data) throws IOException;

    /** 读取文件内容。 */
    byte[] load(String storagePath) throws IOException;

    /** 删除文件，文件不存在时静默忽略。 */
    void delete(String storagePath);
}
