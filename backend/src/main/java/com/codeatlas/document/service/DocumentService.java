package com.codeatlas.document.service;

import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.document.chunker.TextChunker;
import com.codeatlas.document.dto.DocumentDetailVO;
import com.codeatlas.document.dto.DocumentVO;
import com.codeatlas.document.entity.Document;
import com.codeatlas.document.entity.FileChunk;
import com.codeatlas.document.parser.FileParser;
import com.codeatlas.document.repository.DocumentRepository;
import com.codeatlas.document.repository.FileChunkRepository;
import com.codeatlas.project.service.ProjectPermissionService;
import com.codeatlas.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文档服务：上传、查看、删除与切分。
 *
 * <p>处理链路（docs/development.md 第 6 节）：
 * 文件 → 解析 → 文本内容 → Chunk。本阶段不接入 AI。
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final DocumentRepository documentRepository;

    private final FileChunkRepository chunkRepository;

    private final ProjectPermissionService permissionService;

    private final StorageService storageService;

    private final FileParser fileParser;

    private final TextChunker textChunker;

    public DocumentService(DocumentRepository documentRepository,
                           FileChunkRepository chunkRepository,
                           ProjectPermissionService permissionService,
                           StorageService storageService,
                           FileParser fileParser,
                           TextChunker textChunker) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.permissionService = permissionService;
        this.storageService = storageService;
        this.fileParser = fileParser;
        this.textChunker = textChunker;
    }

    /** 上传文档：校验权限与格式 → 解析 → 落盘 → 入库 → 切分。 */
    @Transactional
    public DocumentVO upload(Long projectId, Long userId, MultipartFile file) {
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

        // 解析（不支持的类型会在此抛出）
        String text = fileParser.parse(originalName, data);
        String ext = fileParser.extension(originalName);

        Document document = new Document();
        document.setProjectId(projectId);
        document.setName(originalName);
        document.setFileType(ext);
        document.setFileSize(file.getSize());
        document.setUploadedBy(userId);
        document.setStoragePath("");
        Document saved = documentRepository.save(document);

        try {
            String path = storageService.save(projectId, saved.getId() + "." + ext, data);
            saved.setStoragePath(path);
            saved = documentRepository.save(saved);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }

        int chunkCount = saveChunks(projectId, FileChunk.SourceType.DOCUMENT, saved.getId(), text);
        log.info("文档上传成功 | docId={} | projectId={} | chunks={}", saved.getId(), projectId, chunkCount);

        return DocumentVO.from(saved);
    }

    /** 项目文档列表：项目成员可见。 */
    @Transactional(readOnly = true)
    public List<DocumentVO> list(Long projectId, Long userId) {
        permissionService.requireMember(projectId, userId);
        return documentRepository.findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(projectId)
                .stream()
                .map(DocumentVO::from)
                .toList();
    }

    /** 按名称关键字检索。 */
    @Transactional(readOnly = true)
    public List<DocumentVO> search(Long projectId, Long userId, String keyword) {
        permissionService.requireMember(projectId, userId);
        return documentRepository
                .findByProjectIdAndDeletedFalseAndNameContainingIgnoreCase(projectId, keyword)
                .stream()
                .map(DocumentVO::from)
                .toList();
    }

    /** 查看文档详情（含解析后的正文）。 */
    @Transactional(readOnly = true)
    public DocumentDetailVO getById(Long documentId, Long userId) {
        Document document = requireDocument(documentId);
        permissionService.requireMember(document.getProjectId(), userId);

        String content;
        try {
            byte[] data = storageService.load(document.getStoragePath());
            content = fileParser.parse(document.getName(), data);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED);
        }
        int chunkCount = chunkRepository
                .findBySourceTypeAndSourceIdOrderByChunkIndexAsc(
                        FileChunk.SourceType.DOCUMENT, documentId).size();

        return DocumentDetailVO.from(DocumentVO.from(document), content, chunkCount);
    }

    /** 删除文档：软删除 + 清理 Chunk + 删除物理文件。 */
    @Transactional
    public void delete(Long documentId, Long userId) {
        Document document = requireDocument(documentId);
        permissionService.requireWriter(document.getProjectId(), userId);

        document.setDeleted(true);
        documentRepository.save(document);

        chunkRepository.deleteBySourceTypeAndSourceId(FileChunk.SourceType.DOCUMENT, documentId);
        storageService.delete(document.getStoragePath());

        log.info("文档删除成功 | docId={} | userId={}", documentId, userId);
    }

    private Document requireDocument(Long documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    /** 切分并落库，返回切分块数量。 */
    private int saveChunks(Long projectId, FileChunk.SourceType sourceType,
                           Long sourceId, String text) {
        List<String> chunks = textChunker.chunk(text);
        int index = 0;
        for (String piece : chunks) {
            FileChunk chunk = new FileChunk();
            chunk.setProjectId(projectId);
            chunk.setSourceType(sourceType);
            chunk.setSourceId(sourceId);
            chunk.setChunkIndex(index++);
            chunk.setContent(piece);
            chunkRepository.save(chunk);
        }
        return index;
    }
}
