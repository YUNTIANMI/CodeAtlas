package com.codeatlas.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地文件存储测试：正常读写、项目隔离、删除与目录穿越防护。
 *
 * <p>目录穿越（{@code ../}）是文件上传场景最典型的安全风险，
 * 对应 docs/development.md 第 13 节（安全）中的「文件上传」检查项。
 */
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    /** 存储根目录，故意放在 tempDir 的子目录，便于验证越界路径。 */
    private Path root;

    private LocalStorageService storageService;

    private static final Long PROJECT_ID = 10L;

    @BeforeEach
    void setUp() {
        root = tempDir.resolve("uploads");
        storageService = new LocalStorageService(root.toString());
    }

    // ---------------- 正常流程 ----------------

    @Test
    @DisplayName("保存后可原样读回，且返回相对路径")
    void saveThenLoad() throws IOException {
        byte[] data = "hello codeatlas".getBytes(StandardCharsets.UTF_8);

        String storagePath = storageService.save(PROJECT_ID, "a.txt", data);

        assertEquals("10/a.txt", storagePath);
        assertTrue(Files.exists(root.resolve("10").resolve("a.txt")));
        assertArrayEquals(data, storageService.load(storagePath));
    }

    @Test
    @DisplayName("不同项目的同名文件相互隔离")
    void projectIsolation() throws IOException {
        storageService.save(1L, "a.txt", "one".getBytes(StandardCharsets.UTF_8));
        storageService.save(2L, "a.txt", "two".getBytes(StandardCharsets.UTF_8));

        assertEquals("one", new String(storageService.load("1/a.txt"), StandardCharsets.UTF_8));
        assertEquals("two", new String(storageService.load("2/a.txt"), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("删除后文件不可再读取")
    void deleteThenLoad() throws IOException {
        String storagePath = storageService.save(PROJECT_ID, "a.txt", "data".getBytes(StandardCharsets.UTF_8));

        storageService.delete(storagePath);

        assertFalse(Files.exists(root.resolve("10").resolve("a.txt")));
        assertThrows(IOException.class, () -> storageService.load(storagePath));
    }

    // ---------------- 边界与异常流程 ----------------

    @Test
    @DisplayName("删除不存在的文件不抛异常（幂等）")
    void deleteMissingFileIsIdempotent() {
        assertDoesNotThrow(() -> storageService.delete("10/missing.txt"));
    }

    @Test
    @DisplayName("读取不存在的文件抛出 IOException")
    void loadMissingFile() {
        assertThrows(IOException.class, () -> storageService.load("10/missing.txt"));
    }

    // ---------------- 安全：目录穿越防护 ----------------

    @Test
    @DisplayName("保存时拒绝穿越到项目目录之外的文件名")
    void saveRejectsTraversal() {
        assertThrows(IOException.class,
                () -> storageService.save(PROJECT_ID, "../evil.txt", "x".getBytes(StandardCharsets.UTF_8)));
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    }

    @Test
    @DisplayName("读取时拒绝越过存储根目录的路径")
    void loadRejectsTraversal() {
        assertThrows(IOException.class, () -> storageService.load("../secret.txt"));
    }

    @Test
    @DisplayName("删除时静默忽略越界路径，不会误删根目录外的文件")
    void deleteIgnoresTraversal() throws IOException {
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "should not be deleted");

        storageService.delete("../outside.txt");

        assertTrue(Files.exists(outside));
    }
}
