package com.codeatlas.agent.tool;

import com.codeatlas.document.entity.Document;
import com.codeatlas.document.repository.DocumentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按关键字搜索项目文档。
 */
@Component
public class SearchDocumentTool implements AgentTool {

    private static final int MAX_DOCS = 10;

    private final DocumentRepository documentRepository;

    public SearchDocumentTool(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public String name() {
        return "search_document";
    }

    @Override
    public String description() {
        return "按关键字搜索项目中已上传的文档（md / txt / pdf），返回文档名称与简介。用于查找需求、设计说明等资料。";
    }

    @Override
    public String parametersDescription() {
        return "keyword（必填）：搜索关键字，会匹配文档名称";
    }

    @Override
    public String execute(Long projectId, Map<String, String> input) {
        String keyword = input == null ? null : input.get("keyword");
        if (keyword == null || keyword.isBlank()) {
            return "执行失败：缺少 keyword 参数";
        }

        List<Document> documents = documentRepository
                .findByProjectIdAndDeletedFalseAndNameContainingIgnoreCase(projectId, keyword);

        if (documents.isEmpty()) {
            return "未找到匹配 \"" + keyword + "\" 的文档";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("找到 ").append(documents.size()).append(" 个相关文档：\n");
        for (int i = 0; i < Math.min(documents.size(), MAX_DOCS); i++) {
            Document document = documents.get(i);
            builder.append("- ").append(document.getName())
                    .append("（类型 ").append(document.getFileType()).append("）\n");
        }
        builder.append("\n如需查看内容，请使用 read_file 工具并传入完整文件名。");
        return builder.toString();
    }
}
