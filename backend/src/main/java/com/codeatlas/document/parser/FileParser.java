package com.codeatlas.document.parser;

import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 文件解析器：将上传文件转换为纯文本。
 *
 * <p>支持类型见 docs/development.md 第 6 节：md / txt / pdf / java / cpp / py / js / ts。
 */
@Component
public class FileParser {

    /** 纯文本类扩展名，直接按 UTF-8 解码。 */
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "md", "markdown", "txt",
            "java", "cpp", "cc", "cxx", "c", "h", "hpp",
            "py", "js", "jsx", "ts", "tsx"
    );

    /** 代码扩展名与语言的映射，需与 resolveLanguage 保持一致。 */
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "cpp", "cc", "cxx", "c", "h", "hpp",
            "py", "js", "jsx", "ts", "tsx"
    );

    /** 解析为文本内容。 */
    public String parse(String originalFilename, byte[] data) {
        if (data == null || data.length == 0) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        String ext = extension(originalFilename);
        try {
            if ("pdf".equals(ext)) {
                return parsePdf(data);
            }
            if (TEXT_EXTENSIONS.contains(ext)) {
                return new String(data, StandardCharsets.UTF_8);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED,
                    "文件解析失败：" + ex.getMessage());
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                "不支持的文件类型：" + ext);
    }

    /** 提取扩展名（小写，不含点）。 */
    public String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 判断是否为代码文件。 */
    public boolean isCodeFile(String filename) {
        return CODE_EXTENSIONS.contains(extension(filename));
    }

    /** 将扩展名映射为语言标识，与 database.md 4.6 节一致。 */
    public String resolveLanguage(String filename) {
        String ext = extension(filename);
        return switch (ext) {
            case "java" -> "JAVA";
            case "cpp", "cc", "cxx", "c", "h", "hpp" -> "CPP";
            case "py" -> "PYTHON";
            case "js", "jsx" -> "JAVASCRIPT";
            case "ts", "tsx" -> "TYPESCRIPT";
            default -> null;
        };
    }

    private String parsePdf(byte[] data) throws IOException {
        try (PDDocument document = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
