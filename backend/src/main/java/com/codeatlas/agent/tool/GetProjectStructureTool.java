package com.codeatlas.agent.tool;

import com.codeatlas.code.entity.CodeFile;
import com.codeatlas.code.repository.CodeFileRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 获取项目目录结构。
 */
@Component
public class GetProjectStructureTool implements AgentTool {

    private static final int MAX_PATHS = 80;

    private final CodeFileRepository codeFileRepository;

    public GetProjectStructureTool(CodeFileRepository codeFileRepository) {
        this.codeFileRepository = codeFileRepository;
    }

    @Override
    public String name() {
        return "get_project_structure";
    }

    @Override
    public String description() {
        return "获取项目已上传代码的目录结构，了解模块划分与文件组织方式。";
    }

    @Override
    public String parametersDescription() {
        return "无需参数";
    }

    @Override
    public String execute(Long projectId, Map<String, String> input) {
        List<CodeFile> files = codeFileRepository.findByProjectIdOrderByFilePathAsc(projectId);

        if (files.isEmpty()) {
            return "该项目还没有上传任何代码文件";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("项目共 ").append(files.size()).append(" 个代码文件：\n\n");
        for (int i = 0; i < Math.min(files.size(), MAX_PATHS); i++) {
            builder.append(files.get(i).getFilePath()).append("\n");
        }
        if (files.size() > MAX_PATHS) {
            builder.append("…（仅显示前 ").append(MAX_PATHS).append(" 个）");
        }
        return builder.toString();
    }
}
