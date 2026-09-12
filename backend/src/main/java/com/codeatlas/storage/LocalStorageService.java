package com.codeatlas.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件存储实现。
 *
 * <p>目录结构：{root}/{projectId}/{fileName}
 * 见 docs/architecture.md 第 13 节。
 */
@Service
@ConditionalOnProperty(name = "codeatlas.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(@Value("${codeatlas.storage.root:./uploads}") String rootPath) {
        this.root = Paths.get(rootPath).toAbsolutePath().normalize();
    }

    @Override
    public String save(Long projectId, String storedFileName, byte[] data) throws IOException {
        Path dir = root.resolve(String.valueOf(projectId));
        Files.createDirectories(dir);
        Path target = dir.resolve(storedFileName).normalize();

        // 防目录穿越：确保解析后的路径仍在项目目录内
        if (!target.startsWith(dir)) {
            throw new IOException("非法的存储路径：" + storedFileName);
        }

        Files.write(target, data);
        return root.relativize(target).toString().replace("\\", "/");
    }

    @Override
    public byte[] load(String storagePath) throws IOException {
        Path target = root.resolve(storagePath).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("非法的读取路径：" + storagePath);
        }
        return Files.readAllBytes(target);
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path target = root.resolve(storagePath).normalize();
            if (!target.startsWith(root)) {
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            // 删除失败不阻断业务，交由调用方决定是否回滚
        }
    }
}
