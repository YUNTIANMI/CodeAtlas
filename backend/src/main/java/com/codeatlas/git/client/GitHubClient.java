package com.codeatlas.git.client;

import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * GitHub REST 客户端。
 *
 * <p>只读访问：获取仓库信息与提交记录，不提供任何写操作。
 * 匿名访问有速率限制（60 次/小时），配置 Token 后可提升。
 */
@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

    /** GitHub 提交接口单页最多返回 100 条，传更大的 per_page 也不会生效，只能翻页。 */
    private static final int MAX_PER_PAGE = 100;

    private final RestClient restClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitHubClient(@Value("${codeatlas.git.github.api-base:https://api.github.com}") String apiBase,
                        @Value("${codeatlas.git.github.token:}") String token) {
        // GitHub API 强制要求 User-Agent 头，缺失会返回 403
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(apiBase)
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "CodeAtlas/0.1");
        if (token != null && !token.isBlank()) {
            builder = builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        this.restClient = builder.build();
    }

    /** 设置超时，避免网络不可用时请求无限挂起。 */
    private org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        return factory;
    }

    /** 获取仓库信息（默认分支等）。 */
    public RepoInfo getRepository(String fullName) {
        try {
            // 注意：fullName 形如 owner/repo，其中的斜杠是路径分隔符，
            // 若作为 URI 模板变量传入会被编码成 %2F 导致 404，因此直接拼接
            String response = restClient.get()
                    .uri("/repos/" + fullName)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            return new RepoInfo(
                    text(root, "full_name"),
                    text(root, "default_branch"));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("获取仓库信息失败 | fullName={}", fullName, ex);
            throw new BusinessException(ErrorCode.GIT_SYNC_FAILED,
                    "获取仓库信息失败，请确认仓库地址正确且可访问");
        }
    }

    /**
     * 获取提交列表（按时间倒序）。
     *
     * <p>GitHub 单页最多返回 100 条，limit 大于 100 时自动翻页累加，
     * 直到取满 limit 或远端提交耗尽为止。
     */
    public List<CommitInfo> listCommits(String fullName, int limit) {
        int target = Math.max(limit, 1);
        List<CommitInfo> commits = new ArrayList<>();
        try {
            int page = 1;
            while (commits.size() < target) {
                int perPage = Math.min(target - commits.size(), MAX_PER_PAGE);
                List<CommitInfo> batch = fetchCommitPage(fullName, page, perPage);
                commits.addAll(batch);
                // 返回条数少于请求条数，说明已经到最后一页，继续翻页只会拿到空数组
                if (batch.size() < perPage) {
                    break;
                }
                page++;
            }
            return commits;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("获取提交列表失败 | fullName={} | limit={}", fullName, limit, ex);
            throw new BusinessException(ErrorCode.GIT_SYNC_FAILED, "获取提交列表失败");
        }
    }

    /** 拉取提交列表的单页数据。 */
    private List<CommitInfo> fetchCommitPage(String fullName, int page, int perPage)
            throws JsonProcessingException {
        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/" + fullName + "/commits")
                        .queryParam("per_page", perPage)
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(String.class);

        List<CommitInfo> commits = new ArrayList<>();
        for (JsonNode node : objectMapper.readTree(response)) {
            String sha = text(node, "sha");
            JsonNode commit = node.path("commit");
            JsonNode author = commit.path("author");

            commits.add(new CommitInfo(
                    sha,
                    text(commit, "message"),
                    text(author, "name"),
                    text(author, "email"),
                    text(author, "date")));
        }
        return commits;
    }

    /** 获取单个提交详情（含变更文件与 patch）。 */
    public CommitDetail getCommit(String fullName, String sha) {
        try {
            String response = restClient.get()
                    .uri("/repos/" + fullName + "/commits/" + sha)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode stats = root.path("stats");

            StringBuilder diff = new StringBuilder();
            JsonNode files = root.path("files");
            for (JsonNode file : files) {
                diff.append("文件：").append(text(file, "filename")).append("\n");
                String patch = text(file, "patch");
                if (patch != null && !patch.isBlank()) {
                    diff.append(patch).append("\n");
                }
                diff.append("\n");
            }

            return new CommitDetail(
                    intValue(stats, "additions"),
                    intValue(stats, "deletions"),
                    diff.toString());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("获取提交详情失败 | fullName={} | sha={}", fullName, sha, ex);
            throw new BusinessException(ErrorCode.GIT_SYNC_FAILED, "获取提交详情失败");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Integer intValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? 0 : value.asInt();
    }

    /** 仓库基本信息。 */
    public record RepoInfo(String fullName, String defaultBranch) {
    }

    /** 列表中的提交摘要信息。 */
    public record CommitInfo(String sha, String message, String authorName,
                             String authorEmail, String committedAt) {
    }

    /** 提交详情：增删行数与 diff 内容。 */
    public record CommitDetail(Integer additions, Integer deletions, String diffContent) {
    }
}
