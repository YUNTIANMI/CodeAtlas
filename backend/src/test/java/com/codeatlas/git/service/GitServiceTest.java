package com.codeatlas.git.service;

import com.codeatlas.ai.provider.ChatProvider;
import com.codeatlas.ai.repository.AiExecutionLogRepository;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.git.client.GitHubClient;
import com.codeatlas.git.dto.GitCommitVO;
import com.codeatlas.git.dto.GitRepositoryVO;
import com.codeatlas.git.dto.SyncResultVO;
import com.codeatlas.git.entity.GitCommit;
import com.codeatlas.git.entity.GitRepository;
import com.codeatlas.git.repository.GitCommitRepository;
import com.codeatlas.git.repository.GitRepositoryRepository;
import com.codeatlas.project.repository.ProjectRepository;
import com.codeatlas.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Git 分析服务测试：仓库导入、地址解析、提交同步与 AI 摘要。
 */
@ExtendWith(MockitoExtension.class)
class GitServiceTest {

    @Mock
    private GitRepositoryRepository repositoryRepository;

    @Mock
    private GitCommitRepository commitRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectPermissionService permissionService;

    @Mock
    private GitHubClient gitHubClient;

    @Mock
    private ChatProvider chatProvider;

    @Mock
    private AiExecutionLogRepository executionLogRepository;

    @InjectMocks
    private GitService gitService;

    private static final Long PROJECT_ID = 10L;

    private static final Long USER_ID = 1L;

    private static final Long REPO_ID = 7L;

    private GitRepository repository;

    @BeforeEach
    void setUp() {
        repository = new GitRepository();
        repository.setId(REPO_ID);
        repository.setProjectId(PROJECT_ID);
        repository.setRepoUrl("https://github.com/YUNTIANMI/CodeAtlas");
        repository.setProvider("GITHUB");
        repository.setFullName("YUNTIANMI/CodeAtlas");
        repository.setDefaultBranch("develop");

        lenient().when(projectRepository.findActiveById(anyLong()))
                .thenReturn(Optional.of(new com.codeatlas.project.entity.Project()));
        lenient().when(chatProvider.modelName()).thenReturn("deepseek:deepseek-chat");
    }

    @Test
    @DisplayName("导入仓库：解析 HTTPS 地址")
    void importHttpsUrl() {
        when(repositoryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
        when(gitHubClient.getRepository("YUNTIANMI/CodeAtlas"))
                .thenReturn(new GitHubClient.RepoInfo("YUNTIANMI/CodeAtlas", "develop"));
        when(repositoryRepository.save(any(GitRepository.class))).thenAnswer(invocation -> {
            GitRepository r = invocation.getArgument(0);
            r.setId(REPO_ID);
            return r;
        });

        GitRepositoryVO vo = gitService.importRepository(PROJECT_ID, USER_ID,
                "https://github.com/YUNTIANMI/CodeAtlas", null);

        assertNotNull(vo);
        assertEquals("YUNTIANMI/CodeAtlas", vo.getFullName());
        assertEquals("develop", vo.getDefaultBranch());
        verify(permissionService).requireWriter(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("导入仓库：支持 SSH 与 .git 后缀地址")
    void importSshUrl() {
        when(repositoryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
        when(gitHubClient.getRepository("YUNTIANMI/CodeAtlas"))
                .thenReturn(new GitHubClient.RepoInfo("YUNTIANMI/CodeAtlas", "main"));
        when(repositoryRepository.save(any(GitRepository.class))).thenAnswer(invocation -> {
            GitRepository r = invocation.getArgument(0);
            r.setId(REPO_ID);
            return r;
        });

        GitRepositoryVO vo = gitService.importRepository(PROJECT_ID, USER_ID,
                "git@github.com:YUNTIANMI/CodeAtlas.git", null);

        assertEquals("YUNTIANMI/CodeAtlas", vo.getFullName());
    }

    @Test
    @DisplayName("导入失败：地址格式不正确")
    void importInvalidUrl() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> gitService.importRepository(PROJECT_ID, USER_ID, "不是仓库地址", null));
        assertEquals(ErrorCode.INVALID_REPO_URL, ex.getErrorCode());
        verify(gitHubClient, never()).getRepository(anyString());
    }

    @Test
    @DisplayName("导入失败：项目已配置过仓库")
    void importDuplicate() {
        when(repositoryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(repository));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gitService.importRepository(PROJECT_ID, USER_ID,
                        "https://github.com/YUNTIANMI/CodeAtlas", null));
        assertEquals(ErrorCode.GIT_REPO_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("同步提交：新增入库，已存在的跳过")
    void syncCommits() {
        when(repositoryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(repository));
        when(gitHubClient.listCommits("YUNTIANMI/CodeAtlas", 30)).thenReturn(List.of(
                new GitHubClient.CommitInfo("aaa111", "feat: add user module", "alice",
                        "alice@example.com", "2026-09-13T10:00:00Z"),
                new GitHubClient.CommitInfo("bbb222", "fix: resolve bug", "bob",
                        "bob@example.com", "2026-09-13T11:00:00Z")));
        // 第一条已存在
        when(commitRepository.existsByCommitHash("aaa111")).thenReturn(true);
        when(commitRepository.existsByCommitHash("bbb222")).thenReturn(false);

        SyncResultVO result = gitService.syncCommits(PROJECT_ID, USER_ID, null);

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getAdded());
        assertEquals(1, result.getSkipped());
        verify(commitRepository).saveAll(any());
        assertNotNull(repository.getLastSyncedAt());
    }

    @Test
    @DisplayName("同步失败：尚未配置仓库")
    void syncWithoutRepo() {
        when(repositoryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gitService.syncCommits(PROJECT_ID, USER_ID, 10));
        assertEquals(ErrorCode.GIT_REPO_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("提交列表：按提交时间倒序")
    void listCommits() {
        when(repositoryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(repository));
        when(commitRepository.findByRepoIdOrderByCommittedAtDesc(REPO_ID))
                .thenReturn(List.of(buildCommit(1L, "bbb222")));

        List<GitCommitVO> commits = gitService.listCommits(PROJECT_ID, USER_ID);

        assertEquals(1, commits.size());
        assertEquals("bbb222", commits.get(0).getCommitHash());
        verify(permissionService).requireMember(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("生成摘要：拉取 Diff 交给模型并保存结果")
    void generateSummary() {
        GitCommit commit = buildCommit(1L, "ccc333");
        when(repositoryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(repository));
        when(commitRepository.findById(1L)).thenReturn(Optional.of(commit));
        when(gitHubClient.getCommit("YUNTIANMI/CodeAtlas", "ccc333"))
                .thenReturn(new GitHubClient.CommitDetail(20, 5, "diff --git a/A.java b/A.java"));
        when(chatProvider.chat(anyString(), anyString()))
                .thenReturn("本次提交主要修改用户认证模块，并增加 Token 过期处理。");
        when(commitRepository.save(any(GitCommit.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        GitCommitVO vo = gitService.generateSummary(PROJECT_ID, USER_ID, 1L);

        assertEquals("本次提交主要修改用户认证模块，并增加 Token 过期处理。", vo.getSummary());
        assertTrue(vo.getAnalyzed());
        assertEquals(20, vo.getAdditions());
        assertEquals(5, vo.getDeletions());
        verify(executionLogRepository).save(any(com.codeatlas.ai.entity.AiExecutionLog.class));
    }

    @Test
    @DisplayName("生成摘要失败：提交不存在")
    void generateSummaryNotFound() {
        when(repositoryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(repository));
        when(commitRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gitService.generateSummary(PROJECT_ID, USER_ID, 999L));
        assertEquals(ErrorCode.GIT_COMMIT_NOT_FOUND, ex.getErrorCode());
        verify(chatProvider, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("按提交 ID 查详情：校验所属项目权限")
    void getCommitByCommitId() {
        GitCommit commit = buildCommit(1L, "ddd444");
        when(commitRepository.findById(1L)).thenReturn(Optional.of(commit));
        when(repositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repository));

        GitCommitVO vo = gitService.getCommitByCommitId(1L, USER_ID);

        assertEquals("ddd444", vo.getCommitHash());
        verify(permissionService).requireMember(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("按提交 ID 生成摘要")
    void getSummaryByCommitId() {
        GitCommit commit = buildCommit(2L, "eee555");
        when(commitRepository.findById(2L)).thenReturn(Optional.of(commit));
        when(repositoryRepository.findById(REPO_ID)).thenReturn(Optional.of(repository));
        when(repositoryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(repository));
        when(gitHubClient.getCommit("YUNTIANMI/CodeAtlas", "eee555"))
                .thenReturn(new GitHubClient.CommitDetail(3, 1, "diff"));
        when(chatProvider.chat(anyString(), anyString())).thenReturn("摘要内容");
        when(commitRepository.save(any(GitCommit.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        GitCommitVO vo = gitService.getSummaryByCommitId(2L, USER_ID);

        assertEquals("摘要内容", vo.getSummary());
    }

    @Test
    @DisplayName("AI 失败时记录失败日志并抛出")
    void generateSummaryAiFailure() {
        GitCommit commit = buildCommit(1L, "fff666");
        when(repositoryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(repository));
        when(commitRepository.findById(1L)).thenReturn(Optional.of(commit));
        when(gitHubClient.getCommit(anyString(), anyString()))
                .thenReturn(new GitHubClient.CommitDetail(1, 1, "diff"));
        when(chatProvider.chat(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR));

        assertThrows(BusinessException.class,
                () -> gitService.generateSummary(PROJECT_ID, USER_ID, 1L));
        assertFalse(commit.getAnalyzed());
        verify(executionLogRepository).save(any(com.codeatlas.ai.entity.AiExecutionLog.class));
    }

    private GitCommit buildCommit(Long id, String hash) {
        GitCommit commit = new GitCommit();
        commit.setId(id);
        commit.setRepoId(REPO_ID);
        commit.setCommitHash(hash);
        commit.setMessage("提交信息 " + hash);
        commit.setAuthorName("alice");
        commit.setCommittedAt(LocalDateTime.now());
        commit.setAnalyzed(false);
        return commit;
    }
}
