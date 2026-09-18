package com.codeatlas.document.service;

import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.document.chunker.TextChunker;
import com.codeatlas.document.dto.DocumentVO;
import com.codeatlas.document.entity.Document;
import com.codeatlas.document.entity.FileChunk;
import com.codeatlas.document.parser.FileParser;
import com.codeatlas.document.repository.DocumentRepository;
import com.codeatlas.document.repository.FileChunkRepository;
import com.codeatlas.project.service.ProjectPermissionService;
import com.codeatlas.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档服务测试：覆盖上传、切分、查看、删除与权限校验。
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FileChunkRepository chunkRepository;

    @Mock
    private ProjectPermissionService permissionService;

    @Mock
    private StorageService storageService;

    @Mock
    private FileParser fileParser;

    @Mock
    private TextChunker textChunker;

    @InjectMocks
    private DocumentService documentService;

    private static final Long PROJECT_ID = 10L;

    private static final Long USER_ID = 1L;

    private Document document;

    @BeforeEach
    void setUp() {
        document = new Document();
        document.setId(100L);
        document.setProjectId(PROJECT_ID);
        document.setName("需求文档.md");
        document.setFileType("md");
        document.setFileSize(1024L);
        document.setStoragePath("10/100.md");
        document.setDeleted(false);
    }

    private MockMultipartFile markdownFile() {
        String content = "# 标题\n\n正文内容";
        return new MockMultipartFile("file", "需求文档.md", "text/markdown",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("上传成功：解析 → 落盘 → 入库 → 切分")
    void uploadSuccess() throws IOException {
        when(fileParser.parse(anyString(), any())).thenReturn("# 标题\n\n正文内容");
        when(fileParser.extension("需求文档.md")).thenReturn("md");
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(100L);
            return doc;
        });
        when(storageService.save(eq(PROJECT_ID), eq("100.md"), any())).thenReturn("10/100.md");
        when(textChunker.chunk("# 标题\n\n正文内容")).thenReturn(List.of("第一块", "第二块"));

        DocumentVO result = documentService.upload(PROJECT_ID, USER_ID, markdownFile());

        assertNotNull(result);
        assertEquals("需求文档.md", result.getName());
        assertEquals("md", result.getFileType());
        verify(permissionService).requireWriter(PROJECT_ID, USER_ID);
        verify(storageService).save(eq(PROJECT_ID), eq("100.md"), any());
        verify(chunkRepository, org.mockito.Mockito.times(2)).save(any(FileChunk.class));
    }

    @Test
    @DisplayName("上传失败：文件为空")
    void uploadEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.md", "text/markdown", new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.upload(PROJECT_ID, USER_ID, empty));
        assertEquals(ErrorCode.FILE_EMPTY, ex.getErrorCode());
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("上传失败：超过大小限制")
    void uploadTooLarge() {
        MultipartFile bigFile = mock(MultipartFile.class);
        when(bigFile.getOriginalFilename()).thenReturn("big.md");
        when(bigFile.isEmpty()).thenReturn(false);
        when(bigFile.getSize()).thenReturn(21L * 1024 * 1024);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.upload(PROJECT_ID, USER_ID, bigFile));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ex.getErrorCode());
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("上传失败：无写入权限（VIEWER）")
    void uploadWithoutWritePermission() {
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION))
                .when(permissionService).requireWriter(PROJECT_ID, 2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.upload(PROJECT_ID, 2L, markdownFile()));
        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, ex.getErrorCode());
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("查看失败：文档不存在")
    void getByIdNotFound() {
        when(documentRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.getById(999L, USER_ID));
        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("删除成功：软删除 + 清理 Chunk + 删除物理文件")
    void deleteDocument() throws IOException {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);

        documentService.delete(100L, USER_ID);

        assertTrue(document.getDeleted());
        verify(chunkRepository).deleteBySourceTypeAndSourceId(FileChunk.SourceType.DOCUMENT, 100L);
        verify(storageService).delete("10/100.md");
    }

    @Test
    @DisplayName("列表：仅返回未删除文档")
    void listDocuments() {
        when(documentRepository.findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(PROJECT_ID))
                .thenReturn(List.of(document));

        List<DocumentVO> result = documentService.list(PROJECT_ID, USER_ID);

        assertEquals(1, result.size());
        assertEquals("需求文档.md", result.get(0).getName());
        verify(permissionService).requireMember(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("检索：按关键字过滤")
    void searchDocuments() {
        when(documentRepository
                .findByProjectIdAndDeletedFalseAndNameContainingIgnoreCase(PROJECT_ID, "需求"))
                .thenReturn(List.of(document));

        List<DocumentVO> result = documentService.search(PROJECT_ID, USER_ID, "需求");

        assertEquals(1, result.size());
        assertEquals("需求文档.md", result.get(0).getName());
    }

    @Test
    @DisplayName("删除失败：非成员不能删除")
    void deleteWithoutPermission() {
        when(documentRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(document));
        doThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER))
                .when(permissionService).requireWriter(PROJECT_ID, 999L);

        assertThrows(BusinessException.class, () -> documentService.delete(100L, 999L));
        verify(chunkRepository, never())
                .deleteBySourceTypeAndSourceId(any(FileChunk.SourceType.class), anyLong());
    }
}
