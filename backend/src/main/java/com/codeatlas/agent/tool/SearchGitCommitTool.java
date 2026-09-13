package com.codeatlas.agent.tool;

import com.codeatlas.git.entity.GitCommit;
import com.codeatlas.git.entity.GitRepository;
import com.codeatlas.git.repository.GitCommitRepository;
import com.codeatlas.git.repository.GitRepositoryRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 查询项目的 Git 提交记录。
 */
@Component
public class SearchGitCommitTool implements AgentTool {

    private static final int MAX_COMMITS = 10;

    private final GitRepositoryRepository repositoryRepository;

    private final GitCommitRepository commitRepository;

    public SearchGitCommitTool(GitRepositoryRepository repositoryRepository,
                               GitCommitRepository commitRepository) {
        this.repositoryRepository = repositoryRepository;
        this.commitRepository = commitRepository;
    }

    @Override
    public String name() {
        return "search_git_commit";
    }

    @Override
    public String description() {
        return "查询项目的 Git 提交记录，可按关键字过滤提交信息。用于了解某项功能是什么时候、通过哪些提交加入的。";
    }

    @Override
    public String parametersDescription() {
        return "keyword（可选）：提交信息关键字，不填则返回最近的提交";
    }

    @Override
    public String execute(Long projectId, Map<String, String> input) {
        Optional<GitRepository> repository = repositoryRepository.findByProjectId(projectId);
        if (repository.isEmpty()) {
            return "该项目尚未配置 Git 仓库，无法查询提交记录";
        }

        String keyword = input == null ? null : input.get("keyword");
        List<GitCommit> commits;
        if (keyword == null || keyword.isBlank()) {
            commits = commitRepository
                    .findByRepoIdOrderByCommittedAtDesc(repository.get().getId());
        } else {
            commits = commitRepository
                    .findByRepoIdOrderByCommittedAtDesc(repository.get().getId())
                    .stream()
                    .filter(commit -> commit.getMessage() != null
                            && commit.getMessage().toLowerCase().contains(keyword.toLowerCase()))
                    .toList();
        }

        if (commits.isEmpty()) {
            return keyword == null || keyword.isBlank()
                    ? "该项目还没有同步任何提交记录"
                    : "未找到提交信息包含 \"" + keyword + "\" 的提交";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("找到 ").append(commits.size()).append(" 条提交：\n\n");
        for (int i = 0; i < Math.min(commits.size(), MAX_COMMITS); i++) {
            GitCommit commit = commits.get(i);
            builder.append("- ").append(commit.getCommitHash(), 0, Math.min(7, commit.getCommitHash().length()))
                    .append(" | ").append(commit.getAuthorName() == null ? "未知" : commit.getAuthorName())
                    .append(" | ").append(firstLine(commit.getMessage())).append("\n");
            if (commit.getSummary() != null && !commit.getSummary().isBlank()) {
                builder.append("  摘要：").append(firstLine(commit.getSummary())).append("\n");
            }
        }
        return builder.toString();
    }

    private String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int index = text.indexOf('\n');
        return index > 0 ? text.substring(0, index) : text;
    }
}
