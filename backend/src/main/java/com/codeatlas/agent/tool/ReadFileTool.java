package com.codeatlas.agent.tool;

import com.codeatlas.code.entity.CodeFile;
import com.codeatlas.code.repository.CodeFileRepository;
import com.codeatlas.document.entity.Document;
import com.codeatlas.document.parser.FileParser;
import com.codeatlas.document.repository.DocumentRepository;
import com.codeatlas.storage.StorageService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 读取指定文件的完整内容。
 */
@Component
public class ReadFileTool implements AgentTool {

    private static final int MAX_CONTENT = 3000;

    private final CodeFileRepository codeFileRepository;

    private final DocumentRepository documentRepository;

    private final StorageService storageService;

    private final FileParser fileParser;

    public ReadFileTool(CodeFileRepository codeFileRepository,
                        DocumentRepository documentRepository,
                        StorageService storageService,
                        FileParser fileParser) {
        this.codeFileRepository = codeFileRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.fileParser = fileParser;
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "读取项目内某个文件的完整内容，支持源代码文件与上传的文档。用于查看具体实现细节。";
    }

    @Override
    public String parametersDescription() {
        return "path（必填）：文件路径或文件名，如 src/main/java/UserService.java 或 登录流程说明.md";
    }

    @Override
    public String execute(Long projectId, Map<String, String> input) {
        String path = input == null ? null : input.get("path");
        if (path == null || path.isBlank()) {
            return "执行失败：缺少 path 参数";
        }

        // 优先按代码文件路径匹配
        List<CodeFile> codeFiles = codeFileRepository.findByProjectIdOrderByFilePathAsc(projectId);
        for (CodeFile file : codeFiles) {
            if (file.getFilePath().equalsIgnoreCase(path)
                    || file.getFileName().equalsIgnoreCase(path)) {
                return format("代码文件 " + file.getFilePath(), file.getContent());
            }
        }

        // 再按文档名匹配
        List<Document> documents = documentRepository
                .findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(projectId);
        for (Document document : documents) {
            if (document.getName().equalsIgnoreCase(path)) {
                try {
                    byte[] data = storageService.load(document.getStoragePath());
                    String content = fileParser.parse(document.getName(), data);
                    return format("文档 " + document.getName(), content);
                } catch (Exception ex) {
                    return "读取文档失败：" + ex.getMessage();
                }
            }
        }

        return "未找到文件 \"" + path + "\"";
    }

    private String format(String title, String content) {
        if (content == null || content.isBlank()) {
            return title + "（内容为空）";
        }
        return title + "\n\n" + (content.length() > MAX_CONTENT
                ? content.substring(0, MAX_CONTENT) + "\n…（已截断）"
                : content);
    }
}
