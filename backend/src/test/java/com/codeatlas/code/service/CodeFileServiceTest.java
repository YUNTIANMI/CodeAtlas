package com.codeatlas.code.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 代码文件服务测试：上传、切分、目录结构与删除。
 */
@ExtendWith(MockitoExtension.class)
class CodeFileServiceTest {

    @Mock
    private CodeFileRepository codeFileRepository;

    @Mock
    private FileChunkRepository chunkRepository;

    @Mock
    private ProjectPermissionService permissionService;

    @Mock
    private FileParser fileParser;

    @Mock
    private TextChunker textChunker;

    @InjectMocks
    private CodeFileService codeFileService;

    private static final Long PROJECT_ID = 10L;

    private static final Long USER_ID = 1L;

    private CodeFile codeFile;

    @BeforeEach
    void setUp() {
        codeFile = new CodeFile();
        codeFile.setId(200L);
        codeFile.setProjectId(PROJECT_ID);
        codeFile.setFilePath("src/main/java/Demo.java");
        codeFile.setFileName("Demo.java");
        codeFile.setLanguage("JAVA");
        codeFile.setContent("package demo;\npublic class Demo {}");
    }

    private MockMultipartFile javaFile() {
        String source = "package demo;\npublic class Demo {}\n";
        return new MockMultipartFile("file", "Demo.java", "text/plain",
                source.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("上传代码成功：解析入库并切分")
    void uploadSuccess() {
        when(fileParser.parse(anyString(), any())).thenReturn("package demo;\npublic class Demo {}");
        when(fileParser.resolveLanguage("Demo.java")).thenReturn("JAVA");
        when(codeFileRepository.findByProjectIdAndFilePath(PROJECT_ID, "src/main/java/Demo.java"))
                .thenReturn(Optional.empty());
        when(codeFileRepository.save(any(CodeFile.class))).thenAnswer(invocation -> {
            CodeFile file = invocation.getArgument(0);
            file.setId(200L);
            return file;
        });
        when(textChunker.chunk(anyString())).thenReturn(List.of("块1", "块2"));

        CodeFileVO result = codeFileService.upload(
                PROJECT_ID, USER_ID, javaFile(), "src/main/java/Demo.java");

        assertNotNull(result);
        assertEquals("src/main/java/Demo.java", result.getFilePath());
        assertEquals("JAVA", result.getLanguage());
        verify(permissionService).requireWriter(PROJECT_ID, USER_ID);
        verify(chunkRepository, org.mockito.Mockito.times(2)).save(any(FileChunk.class));
    }

    @Test
    @DisplayName("同路径重复上传：覆盖而非新增")
    void uploadSamePathOverwrites() {
        when(fileParser.parse(anyString(), any())).thenReturn("new content");
        when(fileParser.resolveLanguage("Demo.java")).thenReturn("JAVA");
        when(codeFileRepository.findByProjectIdAndFilePath(PROJECT_ID, "src/main/java/Demo.java"))
                .thenReturn(Optional.of(codeFile));
        when(codeFileRepository.save(any(CodeFile.class))).thenReturn(codeFile);
        when(textChunker.chunk(anyString())).thenReturn(List.of("块1"));

        codeFileService.upload(PROJECT_ID, USER_ID, javaFile(), "src/main/java/Demo.java");

        assertEquals("new content", codeFile.getContent());
        verify(chunkRepository).deleteBySourceTypeAndSourceId(FileChunk.SourceType.CODE, 200L);
    }

    @Test
    @DisplayName("上传失败：无写入权限")
    void uploadWithoutPermission() {
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION))
                .when(permissionService).requireWriter(PROJECT_ID, 2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> codeFileService.upload(PROJECT_ID, 2L, javaFile(), "a.java"));
        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, ex.getErrorCode());
        verify(codeFileRepository, never()).save(any(CodeFile.class));
    }

    @Test
    @DisplayName("目录结构：按路径构建层级树")
    void structureTree() {
        CodeFile controller = new CodeFile();
        controller.setFilePath("src/main/java/UserController.java");
        CodeFile service = new CodeFile();
        service.setFilePath("src/main/java/UserService.java");
        CodeFile rootFile = new CodeFile();
        rootFile.setFilePath("README.md");

        when(codeFileRepository.findByProjectIdOrderByFilePathAsc(PROJECT_ID))
                .thenReturn(List.of(controller, service, rootFile));

        StructureNode root = codeFileService.getStructure(PROJECT_ID, USER_ID);

        assertEquals("root", root.getName());
        assertTrue(root.isDirectory());
        // 根目录下应有 src 与 README.md 两项
        assertEquals(2, root.getChildren().size());

        StructureNode src = root.getChildren().stream()
                .filter(n -> n.getName().equals("src"))
                .findFirst().orElseThrow();
        assertTrue(src.isDirectory());
    }

    @Test
    @DisplayName("查看失败：代码文件不存在")
    void getByIdNotFound() {
        when(codeFileRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> codeFileService.getById(999L, USER_ID));
        assertEquals(ErrorCode.CODE_FILE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("删除成功：清理 Chunk 与记录")
    void deleteCodeFile() {
        when(codeFileRepository.findById(200L)).thenReturn(Optional.of(codeFile));

        codeFileService.delete(200L, USER_ID);

        verify(chunkRepository).deleteBySourceTypeAndSourceId(FileChunk.SourceType.CODE, 200L);
        verify(codeFileRepository).delete(codeFile);
    }

    @Test
    @DisplayName("列表：可按语言筛选")
    void listByLanguage() {
        when(codeFileRepository.findByProjectIdAndLanguageOrderByFilePathAsc(PROJECT_ID, "JAVA"))
                .thenReturn(List.of(codeFile));

        List<CodeFileVO> result = codeFileService.list(PROJECT_ID, USER_ID, "JAVA");

        assertEquals(1, result.size());
        assertEquals("JAVA", result.get(0).getLanguage());
    }

    @Test
    @DisplayName("未指定语言时返回全部")
    void listAll() {
        when(codeFileRepository.findByProjectIdOrderByFilePathAsc(PROJECT_ID))
                .thenReturn(List.of(codeFile));

        List<CodeFileVO> result = codeFileService.list(PROJECT_ID, USER_ID, null);

        assertEquals(1, result.size());
        verify(codeFileRepository).findByProjectIdOrderByFilePathAsc(PROJECT_ID);
    }
}
