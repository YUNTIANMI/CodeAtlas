package com.codeatlas.security;

import com.codeatlas.auth.AuthUser;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.common.GlobalExceptionHandler;
import com.codeatlas.document.chunker.TextChunker;
import com.codeatlas.document.controller.DocumentController;
import com.codeatlas.document.entity.Document;
import com.codeatlas.document.parser.FileParser;
import com.codeatlas.document.repository.DocumentRepository;
import com.codeatlas.document.repository.FileChunkRepository;
import com.codeatlas.document.service.DocumentService;
import com.codeatlas.project.entity.ProjectMember;
import com.codeatlas.project.entity.ProjectRole;
import com.codeatlas.project.repository.ProjectMemberRepository;
import com.codeatlas.project.service.ProjectPermissionService;
import com.codeatlas.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 跨项目越权访问测试（Phase 11 安全验收核心项）。
 *
 * <p>对应 docs/development.md 第 13 节：「重点测试：用户 A 是否能够访问用户 B 的项目」。
 *
 * <p>测试打在真实的 Controller + Service + ProjectPermissionService 链路上，
 * 只把数据访问层替换为 Mock，因此能真实验证：
 * <ul>
 *   <li>「当前用户」只来自认证上下文，伪造请求参数（如 {@code ?userId=1}）无效</li>
 *   <li>非成员访问他人项目的任何接口都返回 403，且不泄露资源内容</li>
 *   <li>项目内角色分级生效：VIEWER 不能写入</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CrossProjectAccessControlTest {

    /** 用户 A：项目 10 的所有者。 */
    private static final Long OWNER_ID = 1L;

    /** 用户 B：不属于项目 10。 */
    private static final Long OUTSIDER_ID = 2L;

    private static final Long PROJECT_ID = 10L;

    private static final Long DOCUMENT_ID = 100L;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FileChunkRepository chunkRepository;

    @Mock
    private ProjectMemberRepository memberRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private FileParser fileParser;

    @Mock
    private TextChunker textChunker;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProjectPermissionService permissionService = new ProjectPermissionService(memberRepository);
        DocumentService documentService = new DocumentService(
                documentRepository, chunkRepository, permissionService,
                storageService, fileParser, textChunker);

        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(documentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Authentication principal(Long userId) {
        AuthUser authUser = new AuthUser(userId, "user" + userId, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return new UsernamePasswordAuthenticationToken(
                authUser, null, authUser.getAuthorities());
    }

    private ProjectMember member(Long userId, ProjectRole role) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(PROJECT_ID);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private Document documentOfProject(Long id, Long projectId) {
        Document document = new Document();
        document.setId(id);
        document.setProjectId(projectId);
        document.setName("design.md");
        document.setFileType("md");
        document.setFileSize(10L);
        document.setStoragePath("10/100.md");
        return document;
    }

    private MockMultipartFile markdownFile(String filename) {
        return new MockMultipartFile("file", filename, "text/markdown",
                "# hello".getBytes(StandardCharsets.UTF_8));
    }

    /* ---------------- 用户 B 访问用户 A 的项目：一律 403 ---------------- */

    @Test
    @DisplayName("用户 B 查看用户 A 的文档列表：403 且业务码为 2002")
    void outsiderCannotListDocuments() throws Exception {
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OUTSIDER_ID))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/projects/{projectId}/documents", PROJECT_ID)
                        .principal(principal(OUTSIDER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_PROJECT_MEMBER.getCode()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(documentRepository, never())
                .findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(anyLong());
    }

    @Test
    @DisplayName("用户 B 伪造 ?userId=1 参数冒充用户 A：依然 403")
    void outsiderCannotImpersonateViaQueryParameter() throws Exception {
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OUTSIDER_ID))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/projects/{projectId}/documents", PROJECT_ID)
                        .param("userId", String.valueOf(OWNER_ID))
                        .principal(principal(OUTSIDER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_PROJECT_MEMBER.getCode()));
    }

    @Test
    @DisplayName("用户 B 上传文档到用户 A 的项目：403")
    void outsiderCannotUploadDocument() throws Exception {
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OUTSIDER_ID))
                .willReturn(Optional.empty());

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/documents", PROJECT_ID)
                        .file(markdownFile("evil.md"))
                        .principal(principal(OUTSIDER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_PROJECT_MEMBER.getCode()));

        verify(documentRepository, never()).save(any(Document.class));
        verify(storageService, never()).save(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("用户 B 读取用户 A 项目的文档正文：403（不泄露内容）")
    void outsiderCannotReadDocumentDetail() throws Exception {
        given(documentRepository.findByIdAndDeletedFalse(DOCUMENT_ID))
                .willReturn(Optional.of(documentOfProject(DOCUMENT_ID, PROJECT_ID)));
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OUTSIDER_ID))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/documents/{documentId}", DOCUMENT_ID)
                        .principal(principal(OUTSIDER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_PROJECT_MEMBER.getCode()));

        verify(storageService, never()).load(anyString());
    }

    @Test
    @DisplayName("用户 B 删除用户 A 项目的文档：403，且文档未被改动")
    void outsiderCannotDeleteDocument() throws Exception {
        Document document = documentOfProject(DOCUMENT_ID, PROJECT_ID);
        given(documentRepository.findByIdAndDeletedFalse(DOCUMENT_ID))
                .willReturn(Optional.of(document));
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OUTSIDER_ID))
                .willReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/documents/{documentId}", DOCUMENT_ID)
                        .principal(principal(OUTSIDER_ID)))
                .andExpect(status().isForbidden());

        verify(documentRepository, never()).save(any(Document.class));
        verify(storageService, never()).delete(anyString());
    }

    @Test
    @DisplayName("用户 B 检索用户 A 项目的文档：403")
    void outsiderCannotSearchDocuments() throws Exception {
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OUTSIDER_ID))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/projects/{projectId}/documents/search", PROJECT_ID)
                        .param("keyword", "设计")
                        .principal(principal(OUTSIDER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_PROJECT_MEMBER.getCode()));
    }

    /* ---------------- 项目成员：正常访问 ---------------- */

    @Test
    @DisplayName("项目所有者查看自己的文档列表：200")
    void ownerCanListOwnDocuments() throws Exception {
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .willReturn(Optional.of(member(OWNER_ID, ProjectRole.OWNER)));
        given(documentRepository.findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(PROJECT_ID))
                .willReturn(List.of(documentOfProject(DOCUMENT_ID, PROJECT_ID)));

        mockMvc.perform(get("/api/v1/projects/{projectId}/documents", PROJECT_ID)
                        .principal(principal(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].id").value(DOCUMENT_ID))
                .andExpect(jsonPath("$.data[0].name").value("design.md"));
    }

    @Test
    @DisplayName("认证主体异常时不放行（fail-closed），且不触碰任何数据访问")
    void abnormalPrincipalIsDenied() throws Exception {
        // 正常情况下 JwtAuthenticationFilter 只会写入 AuthUser，这里是防御性校验：
        // 一旦主体不是 AuthUser，宁可报错也不能猜一个 userId 出来
        Authentication anonymous = new UsernamePasswordAuthenticationToken(
                "anonymous", null, List.of());

        mockMvc.perform(get("/api/v1/projects/{projectId}/documents", PROJECT_ID)
                        .principal(anonymous))
                .andExpect(status().is5xxServerError());

        verify(memberRepository, never()).findByProjectIdAndUserId(anyLong(), anyLong());
    }

    /* ---------------- 项目内角色分级 ---------------- */

    @Test
    @DisplayName("VIEWER 只能查看，不能上传：403 且业务码为 2003")
    void viewerCannotUpload() throws Exception {
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OUTSIDER_ID))
                .willReturn(Optional.of(member(OUTSIDER_ID, ProjectRole.VIEWER)));

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/documents", PROJECT_ID)
                        .file(markdownFile("readme.md"))
                        .principal(principal(OUTSIDER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_PERMISSION.getCode()));

        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("VIEWER 可以查看：200")
    void viewerCanRead() throws Exception {
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OUTSIDER_ID))
                .willReturn(Optional.of(member(OUTSIDER_ID, ProjectRole.VIEWER)));
        given(documentRepository.findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(PROJECT_ID))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/projects/{projectId}/documents", PROJECT_ID)
                        .principal(principal(OUTSIDER_ID)))
                .andExpect(status().isOk());
    }

    /* ---------------- 上传边界 ---------------- */

    @Test
    @DisplayName("不存在的文档：404，不泄露其所属项目")
    void missingDocumentReturnsNotFound() throws Exception {
        given(documentRepository.findByIdAndDeletedFalse(DOCUMENT_ID))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/documents/{documentId}", DOCUMENT_ID)
                        .principal(principal(OUTSIDER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.DOCUMENT_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("空文件：400")
    void uploadRejectsEmptyFile() throws Exception {
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .willReturn(Optional.of(member(OWNER_ID, ProjectRole.OWNER)));

        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.md", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/documents", PROJECT_ID)
                        .file(empty)
                        .principal(principal(OWNER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.FILE_EMPTY.getCode()));
    }

    @Test
    @DisplayName("超长文件名：400，避免异常输入写入数据库")
    void uploadRejectsOverlongFileName() throws Exception {
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .willReturn(Optional.of(member(OWNER_ID, ProjectRole.OWNER)));

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/documents", PROJECT_ID)
                        .file(markdownFile("a".repeat(256) + ".md"))
                        .principal(principal(OWNER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verify(storageService, never()).save(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("不支持的文件类型：400")
    void uploadRejectsUnsupportedFileType() throws Exception {
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .willReturn(Optional.of(member(OWNER_ID, ProjectRole.OWNER)));
        given(fileParser.parse(anyString(), any()))
                .willThrow(new com.codeatlas.common.BusinessException(
                        ErrorCode.UNSUPPORTED_FILE_TYPE));

        mockMvc.perform(multipart("/api/v1/projects/{projectId}/documents", PROJECT_ID)
                        .file(markdownFile("payload.exe"))
                        .principal(principal(OWNER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_FILE_TYPE.getCode()));

        verify(storageService, never()).save(anyLong(), anyString(), any());
    }
}
