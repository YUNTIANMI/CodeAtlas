package com.codeatlas.git.service;

import com.codeatlas.ai.entity.AiExecutionLog;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 分析服务。
 *
 * <p>分两步实现（docs/development.md 第 10 节）：
 * 1. 导入仓库 → 同步 → 获取 Commit → 保存
 * 2. Commit → Diff → AI → 提交摘要
 *
 * <p>本服务只读远端仓库，不执行任何 Push 或修改操作。
 */
@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private static final String SYSTEM_PROMPT = """
            你是一位资深软件工程师，负责为代码提交生成简洁的中文摘要。

            要求：
            - 用一到两句话说明本次提交做了什么
            - 指出主要影响的模块或文件
            - 如果涉及行为变更（如新增校验、修改逻辑），请明确指出
            - 只描述事实，不要评价代码好坏
            - 不要复述完整的提交信息
            """;

    /** 未指定 limit 时默认同步的提交条数。 */
    private static final int DEFAULT_SYNC_LIMIT = 30;

    /** 单次同步允许拉取的最大提交条数，避免一次同步请求过多触发 GitHub 限流。 */
    private static final int MAX_SYNC_LIMIT = 500;

    private static final Pattern HTTPS_PATTERN =
            Pattern.compile("https?://(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$");

    private static final Pattern SSH_PATTERN =
            Pattern.compile("git@github\\.com:([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$");

    private static final Pattern SHORT_PATTERN = Pattern.compile("^([\\w.-]+)/([\\w.-]+)$");

    private final GitRepositoryRepository repositoryRepository;

    private final GitCommitRepository commitRepository;

    private final ProjectRepository projectRepository;

    private final ProjectPermissionService permissionService;

    private final GitHubClient gitHubClient;

    private final ChatProvider chatProvider;

    private final AiExecutionLogRepository executionLogRepository;

    public GitService(GitRepositoryRepository repositoryRepository,
                      GitCommitRepository commitRepository,
                      ProjectRepository projectRepository,
                      ProjectPermissionService permissionService,
                      GitHubClient gitHubClient,
                      ChatProvider chatProvider,
                      AiExecutionLogRepository executionLogRepository) {
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
        this.projectRepository = projectRepository;
        this.permissionService = permissionService;
        this.gitHubClient = gitHubClient;
        this.chatProvider = chatProvider;
        this.executionLogRepository = executionLogRepository;
    }

    /** 导入仓库：校验地址、拉取仓库信息并保存配置。 */
    @Transactional
    public GitRepositoryVO importRepository(Long projectId, Long userId, String repoUrl,
                                            String accessTokenRef) {
        permissionService.requireWriter(projectId, userId);
        requireProject(projectId);

        String fullName = parseFullName(repoUrl);
        if (fullName == null) {
            throw new BusinessException(ErrorCode.INVALID_REPO_URL,
                    "无法解析仓库地址，请填写形如 https://github.com/owner/repo 的地址");
        }

        Optional<GitRepository> existing = repositoryRepository.findByProjectId(projectId);
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.GIT_REPO_EXISTS, "该项目已配置 Git 仓库");
        }

        GitHubClient.RepoInfo info = gitHubClient.getRepository(fullName);
        if (info == null || info.fullName() == null) {
            throw new BusinessException(ErrorCode.GIT_SYNC_FAILED, "无法获取仓库信息");
        }

        GitRepository repository = new GitRepository();
        repository.setProjectId(projectId);
        repository.setRepoUrl(repoUrl);
        repository.setProvider("GITHUB");
        repository.setFullName(info.fullName());
        repository.setDefaultBranch(info.defaultBranch());
        repository.setAccessTokenRef(accessTokenRef);
        repository.setSyncStatus(GitRepository.SyncStatus.IDLE.name());

        GitRepository saved = repositoryRepository.save(repository);
        log.info("Git 仓库导入成功 | projectId={} | repo={}", projectId, info.fullName());
        return GitRepositoryVO.from(saved);
    }

    /** 同步提交：拉取远端提交列表，新增的入库，已存在的跳过。 */
    @Transactional
    public SyncResultVO syncCommits(Long projectId, Long userId, Integer limit) {
        permissionService.requireWriter(projectId, userId);
        GitRepository repository = requireRepository(projectId);

        int size = (limit == null || limit <= 0)
                ? DEFAULT_SYNC_LIMIT
                : Math.min(limit, MAX_SYNC_LIMIT);
        List<GitHubClient.CommitInfo> remote = gitHubClient.listCommits(repository.getFullName(), size);

        int added = 0;
        int skipped = 0;
        List<GitCommit> toSave = new ArrayList<>();
        for (GitHubClient.CommitInfo info : remote) {
            if (info.sha() == null) {
                continue;
            }
            // 判重必须限定在当前仓库内：同一个提交可能已被其它项目导入
            if (commitRepository.existsByRepoIdAndCommitHash(repository.getId(), info.sha())) {
                skipped++;
                continue;
            }
            GitCommit commit = new GitCommit();
            commit.setRepoId(repository.getId());
            commit.setCommitHash(info.sha());
            commit.setMessage(info.message());
            commit.setAuthorName(info.authorName());
            commit.setAuthorEmail(info.authorEmail());
            commit.setCommittedAt(parseTime(info.committedAt()));
            commit.setAnalyzed(false);
            toSave.add(commit);
            added++;
        }
        commitRepository.saveAll(toSave);

        repository.setLastSyncedAt(LocalDateTime.now());
        repository.setSyncStatus(GitRepository.SyncStatus.IDLE.name());
        repositoryRepository.save(repository);

        log.info("Git 提交同步完成 | repo={} | added={} | skipped={}",
                repository.getFullName(), added, skipped);
        return new SyncResultVO(remote.size(), added, skipped);
    }

    /** 提交列表。 */
    @Transactional(readOnly = true)
    public List<GitCommitVO> listCommits(Long projectId, Long userId) {
        permissionService.requireMember(projectId, userId);
        GitRepository repository = requireRepository(projectId);
        return commitRepository.findByRepoIdOrderByCommittedAtDesc(repository.getId())
                .stream()
                .map(GitCommitVO::from)
                .toList();
    }

    /** 提交详情。 */
    @Transactional(readOnly = true)
    public GitCommitVO getCommit(Long projectId, Long userId, Long commitId) {
        permissionService.requireMember(projectId, userId);
        requireRepository(projectId);
        GitCommit commit = commitRepository.findById(commitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GIT_COMMIT_NOT_FOUND));
        return GitCommitVO.from(commit);
    }

    /**
     * 生成提交摘要：拉取 Diff → 交给模型 → 保存摘要。
     */
    @Transactional
    public GitCommitVO generateSummary(Long projectId, Long userId, Long commitId) {
        permissionService.requireMember(projectId, userId);
        GitRepository repository = requireRepository(projectId);
        GitCommit commit = commitRepository.findById(commitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GIT_COMMIT_NOT_FOUND));

        // 首次分析时补充 diff 与增删行数
        if (commit.getDiffContent() == null || commit.getDiffContent().isBlank()) {
            GitHubClient.CommitDetail detail =
                    gitHubClient.getCommit(repository.getFullName(), commit.getCommitHash());
            commit.setDiffContent(detail.diffContent());
            commit.setAdditions(detail.additions());
            commit.setDeletions(detail.deletions());
        }

        String summary;
        long start = System.currentTimeMillis();
        try {
            String prompt = buildSummaryPrompt(commit);
            summary = chatProvider.chat(SYSTEM_PROMPT, prompt);
        } catch (BusinessException ex) {
            recordLog(projectId, userId, (int) (System.currentTimeMillis() - start), false,
                    ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("提交摘要生成失败 | commitId={}", commitId, ex);
            recordLog(projectId, userId, (int) (System.currentTimeMillis() - start), false,
                    ex.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务调用失败");
        }

        recordLog(projectId, userId, (int) (System.currentTimeMillis() - start), true, null);

        commit.setSummary(summary);
        commit.setAnalyzed(true);
        GitCommit saved = commitRepository.save(commit);

        log.info("提交摘要生成成功 | commitId={} | repo={}", commitId, repository.getFullName());
        return GitCommitVO.from(saved);
    }

    /** 按提交 ID 查详情：通过提交反查所属仓库与项目后校验权限。 */
    @Transactional(readOnly = true)
    public GitCommitVO getCommitByCommitId(Long commitId, Long userId) {
        GitCommit commit = requireCommit(commitId);
        GitRepository repository = requireRepositoryById(commit.getRepoId());
        permissionService.requireMember(repository.getProjectId(), userId);
        return GitCommitVO.from(commit);
    }

    /** 按提交 ID 生成摘要。 */
    @Transactional
    public GitCommitVO getSummaryByCommitId(Long commitId, Long userId) {
        GitCommit commit = requireCommit(commitId);
        GitRepository repository = requireRepositoryById(commit.getRepoId());
        return generateSummary(repository.getProjectId(), userId, commitId);
    }

    private GitCommit requireCommit(Long commitId) {
        return commitRepository.findById(commitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GIT_COMMIT_NOT_FOUND));
    }

    private GitRepository requireRepositoryById(Long repoId) {
        return repositoryRepository.findById(repoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GIT_REPO_NOT_FOUND));
    }

    private String buildSummaryPrompt(GitCommit commit) {
        StringBuilder builder = new StringBuilder();
        builder.append("提交信息：").append(commit.getMessage() == null ? "（无）" : commit.getMessage()).append("\n\n");
        if (commit.getAdditions() != null || commit.getDeletions() != null) {
            builder.append("变更规模：+").append(commit.getAdditions() == null ? 0 : commit.getAdditions())
                    .append(" / -").append(commit.getDeletions() == null ? 0 : commit.getDeletions())
                    .append("\n\n");
        }
        String diff = commit.getDiffContent();
        if (diff == null || diff.isBlank()) {
            builder.append("（该提交没有可用的 diff 内容）");
        } else {
            // 截断超长 diff，避免超出模型上下文
            int maxLength = 6000;
            builder.append("变更内容：\n")
                    .append(diff.length() > maxLength ? diff.substring(0, maxLength) + "\n…（已截断）" : diff);
        }
        return builder.toString();
    }

    /** 解析仓库地址，支持 HTTPS / SSH / owner/repo 三种写法。 */
    private String parseFullName(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return null;
        }
        String url = repoUrl.trim();

        Matcher https = HTTPS_PATTERN.matcher(url);
        if (https.matches()) {
            return https.group(1) + "/" + https.group(2);
        }
        Matcher ssh = SSH_PATTERN.matcher(url);
        if (ssh.matches()) {
            return ssh.group(1) + "/" + ssh.group(2);
        }
        Matcher shortForm = SHORT_PATTERN.matcher(url);
        if (shortForm.matches()) {
            return url;
        }
        return null;
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.replace("Z", ""));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private void requireProject(Long projectId) {
        projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private GitRepository requireRepository(Long projectId) {
        return repositoryRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GIT_REPO_NOT_FOUND));
    }

    private void recordLog(Long projectId, Long userId, int elapsedMs,
                           boolean success, String error) {
        AiExecutionLog logEntity = new AiExecutionLog();
        logEntity.setProjectId(projectId);
        logEntity.setUserId(userId);
        logEntity.setRequestType("GIT_COMMIT_SUMMARY");
        logEntity.setModel(chatProvider.modelName());
        logEntity.setExecutionTimeMs(elapsedMs);
        logEntity.setSuccess(success);
        logEntity.setErrorMessage(error);
        executionLogRepository.save(logEntity);
    }
}
