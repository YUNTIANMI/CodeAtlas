package com.codeatlas.agent.tool;

import com.codeatlas.code.entity.CodeFile;
import com.codeatlas.code.repository.CodeFileRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 按关键字搜索项目代码。
 */
@Component
public class SearchCodeTool implements AgentTool {

    private static final int MAX_FILES = 5;

    private static final int MAX_CONTENT = 800;

    private final CodeFileRepository codeFileRepository;

    public SearchCodeTool(CodeFileRepository codeFileRepository) {
        this.codeFileRepository = codeFileRepository;
    }

    @Override
    public String name() {
        return "search_code";
    }

    @Override
    public String description() {
        return "按关键字搜索项目中的源代码文件，返回文件路径与代码片段。用于查找某个功能或类在哪些文件中实现。";
    }

    @Override
    public String parametersDescription() {
        return "keyword（必填）：搜索关键字，如类名、方法名或路径片段";
    }

    @Override
    public String execute(Long projectId, Map<String, String> input) {
        String keyword = input == null ? null : input.get("keyword");
        if (keyword == null || keyword.isBlank()) {
            return "执行失败：缺少 keyword 参数";
        }

        List<CodeFile> files = codeFileRepository
                .findByProjectIdAndFilePathContainingIgnoreCase(projectId, keyword);

        if (files.isEmpty()) {
            return "未找到匹配 \"" + keyword + "\" 的代码文件";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("找到 ").append(files.size()).append(" 个相关文件：\n\n");
        for (int i = 0; i < Math.min(files.size(), MAX_FILES); i++) {
            CodeFile file = files.get(i);
            builder.append("文件：").append(file.getFilePath()).append("\n");
            if (file.getContent() != null && !file.getContent().isBlank()) {
                String content = file.getContent();
                builder.append(content.length() > MAX_CONTENT
                        ? content.substring(0, MAX_CONTENT) + "\n…（已截断）"
                        : content);
                builder.append("\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }
}
