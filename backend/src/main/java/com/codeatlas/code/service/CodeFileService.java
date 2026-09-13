package com.codeatlas.code.service;

import com.codeatlas.code.dto.CodeFileDetailVO;
import com.codeatlas.code.dto.CodeFileVO;
import com.codeatlas.code.dto.StructureNode;
import com.codeatlas.code.entity.CodeFile;
import com.codeatlas.code.repository.CodeFileRepository;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.document.chunker.TextChunker;
import com.codeatlas.document.entity.FileChunk;
import com.codeatlas.document.parser.FileParser;
import com.codeatlas.document.repository.FileChunkRepository;
import com.codeatlas.project.service.ProjectPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 代码文件服务：上传、查看、删除与目录结构。
 *
 * <p>代码正文直接存入 code_files.content（见 docs/database.md 4.6 节），
 * 解析后同样切分为 Chunk，供 Phase 5 构建向量库。
 */
@Service
public class CodeFileService {

    private static final Logger log = LoggerFactory.getLogger(CodeFileService.class);

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    /** code_files.file_path 列宽，超长会在入库时抛数据库异常，这里提前拦截。 */
    private static final int MAX_PATH_LENGTH = 500;

    private final CodeFileRepository codeFileRepository;

    private final FileChunkRepository chunkRepository;

    private final ProjectPermissionService permissionService;

    private final FileParser fileParser;

    private final TextChunker textChunker;

    public CodeFileService(CodeFileRepository codeFileRepository,
                           FileChunkRepository chunkRepository,
                           ProjectPermissionService permissionService,
                           FileParser fileParser,
                           TextChunker textChunker) {
        this.codeFileRepository = codeFileRepository;
        this.chunkRepository = chunkRepository;
        this.permissionService = permissionService;
        this.fileParser = fileParser;
        this.textChunker = textChunker;
    }

    /** 上传代码文件：解析为文本后入库并切分。 */
    @Transactional
    public CodeFileVO upload(Long projectId, Long userId, MultipartFile file, String filePath) {
        permissionService.requireWriter(projectId, userId);

        String originalName = file.getOriginalFilename();
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }

        String content = fileParser.parse(originalName, data);
        String resolvedPath = resolveFilePath(filePath, originalName);

        CodeFile codeFile = codeFileRepository
                .findByProjectIdAndFilePath(projectId, resolvedPath)
                .orElseGet(CodeFile::new);

        codeFile.setProjectId(projectId);
        codeFile.setFilePath(resolvedPath);
        codeFile.setFileName(originalName);
        codeFile.setLanguage(fileParser.resolveLanguage(originalName));
        codeFile.setContent(content);
        codeFile.setFileSize(file.getSize());
        codeFile.setIndexed(false);

        CodeFile saved = codeFileRepository.save(codeFile);

        chunkRepository.deleteBySourceTypeAndSourceId(FileChunk.SourceType.CODE, saved.getId());
        int chunkCount = saveChunks(projectId, saved.getId(), content);

        log.info("代码上传成功 | fileId={} | path={} | chunks={}",
                saved.getId(), resolvedPath, chunkCount);
        return CodeFileVO.from(saved);
    }

    /** 代码文件列表：项目成员可见。 */
    @Transactional(readOnly = true)
    public List<CodeFileVO> list(Long projectId, Long userId, String language) {
        permissionService.requireMember(projectId, userId);
        List<CodeFile> files = (language == null || language.isBlank())
                ? codeFileRepository.findByProjectIdOrderByFilePathAsc(projectId)
                : codeFileRepository.findByProjectIdAndLanguageOrderByFilePathAsc(projectId, language);
        return files.stream().map(CodeFileVO::from).toList();
    }

    /** 查看代码详情（含源码与切分块数量）。 */
    @Transactional(readOnly = true)
    public CodeFileDetailVO getById(Long fileId, Long userId) {
        CodeFile codeFile = requireCodeFile(fileId);
        permissionService.requireMember(codeFile.getProjectId(), userId);

        int chunkCount = chunkRepository
                .findBySourceTypeAndSourceIdOrderByChunkIndexAsc(FileChunk.SourceType.CODE, fileId)
                .size();
        return CodeFileDetailVO.from(CodeFileVO.from(codeFile), codeFile.getContent(), chunkCount);
    }

    /** 项目目录结构：由 file_path 构建树。 */
    @Transactional(readOnly = true)
    public StructureNode getStructure(Long projectId, Long userId) {
        permissionService.requireMember(projectId, userId);

        List<CodeFile> files = codeFileRepository.findByProjectIdOrderByFilePathAsc(projectId);
        StructureNode root = StructureNode.directory("root", "");
        Map<String, StructureNode> directories = new HashMap<>();
        directories.put("", root);

        for (CodeFile file : files) {
            String path = file.getFilePath().replace("\\", "/");
            int idx = path.lastIndexOf('/');
            String dirPath = idx > 0 ? path.substring(0, idx) : "";
            String fileName = idx >= 0 ? path.substring(idx + 1) : path;
            StructureNode parent = ensureDirectory(dirPath, directories, root);
            parent.getChildren().add(StructureNode.file(fileName, path));
        }
        return root;
    }

    /** 删除代码文件及其 Chunk。 */
    @Transactional
    public void delete(Long fileId, Long userId) {
        CodeFile codeFile = requireCodeFile(fileId);
        permissionService.requireWriter(codeFile.getProjectId(), userId);

        chunkRepository.deleteBySourceTypeAndSourceId(FileChunk.SourceType.CODE, fileId);
        codeFileRepository.delete(codeFile);

        log.info("代码文件删除成功 | fileId={} | userId={}", fileId, userId);
    }

    /**
     * 规整入库路径。
     *
     * <p>前端上传源码目录时会带上相对路径（如 src/main/java/UserService.java），
     * 未提供时退化为纯文件名。统一分隔符并剔除空段与 "." / ".."，
     * 避免同一文件因写法不同而重复入库（code_files 上有 UNIQUE(project_id, file_path)）。
     */
    private String resolveFilePath(String filePath, String originalName) {
        if (filePath == null || filePath.isBlank()) {
            return originalName;
        }
        String normalized = Arrays.stream(filePath.replace("\\", "/").split("/"))
                .filter(segment -> !segment.isBlank() && !".".equals(segment) && !"..".equals(segment))
                .collect(Collectors.joining("/"));
        if (normalized.isEmpty()) {
            return originalName;
        }
        if (normalized.length() > MAX_PATH_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "文件路径超过 " + MAX_PATH_LENGTH + " 字符：" + normalized);
        }
        return normalized;
    }

    private CodeFile requireCodeFile(Long fileId) {
        return codeFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_FILE_NOT_FOUND));
    }

    private StructureNode ensureDirectory(String dirPath, Map<String, StructureNode> directories,
                                          StructureNode root) {
        if (dirPath.isEmpty()) {
            return root;
        }
        StructureNode existing = directories.get(dirPath);
        if (existing != null) {
            return existing;
        }
        int idx = dirPath.lastIndexOf('/');
        String parentPath = idx > 0 ? dirPath.substring(0, idx) : "";
        StructureNode parent = ensureDirectory(parentPath, directories, root);

        String name = idx >= 0 ? dirPath.substring(idx + 1) : dirPath;
        StructureNode node = StructureNode.directory(name, dirPath);
        parent.getChildren().add(node);
        directories.put(dirPath, node);
        return node;
    }

    private int saveChunks(Long projectId, Long sourceId, String text) {
        List<String> chunks = textChunker.chunk(text);
        int index = 0;
        for (String piece : chunks) {
            FileChunk chunk = new FileChunk();
            chunk.setProjectId(projectId);
            chunk.setSourceType(FileChunk.SourceType.CODE);
            chunk.setSourceId(sourceId);
            chunk.setChunkIndex(index++);
            chunk.setContent(piece);
            chunkRepository.save(chunk);
        }
        return index;
    }
}
