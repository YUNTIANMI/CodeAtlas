package com.codeatlas.document.parser;

import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件解析器测试：覆盖 md / txt / 代码 / pdf 与异常类型。
 */
class FileParserTest {

    private FileParser parser;

    @BeforeEach
    void setUp() {
        parser = new FileParser();
    }

    @Test
    @DisplayName("解析 Markdown")
    void parseMarkdown() {
        String content = "# 标题\n\n这是正文内容。";

        String result = parser.parse("README.md", content.getBytes(StandardCharsets.UTF_8));

        assertTrue(result.contains("# 标题"));
        assertTrue(result.contains("这是正文内容。"));
    }

    @Test
    @DisplayName("解析 TXT")
    void parseTxt() {
        String result = parser.parse("notes.txt", "纯文本内容".getBytes(StandardCharsets.UTF_8));

        assertEquals("纯文本内容", result);
    }

    @Test
    @DisplayName("解析 Java 源码并识别语言")
    void parseJava() {
        String source = "package com.example;\npublic class Demo {}\n";

        String result = parser.parse("Demo.java", source.getBytes(StandardCharsets.UTF_8));

        assertTrue(result.contains("public class Demo"));
        assertEquals("JAVA", parser.resolveLanguage("Demo.java"));
        assertTrue(parser.isCodeFile("Demo.java"));
    }

    @Test
    @DisplayName("语言映射：cpp / py / js / ts")
    void languageMapping() {
        assertEquals("CPP", parser.resolveLanguage("main.cpp"));
        assertEquals("PYTHON", parser.resolveLanguage("app.py"));
        assertEquals("JAVASCRIPT", parser.resolveLanguage("index.js"));
        assertEquals("TYPESCRIPT", parser.resolveLanguage("app.ts"));
        assertNull(parser.resolveLanguage("readme.md"));
    }

    @Test
    @DisplayName("解析 PDF：真实生成 PDF 并抽取文本")
    void parsePdf() throws Exception {
        byte[] pdf = buildPdf("Hello CodeAtlas PDF");

        String result = parser.parse("doc.pdf", pdf);

        assertTrue(result.contains("Hello CodeAtlas PDF"),
                "PDF 文本抽取失败，实际内容：" + result);
    }

    @Test
    @DisplayName("空文件抛 FILE_EMPTY")
    void emptyFile() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse("a.md", new byte[0]));
        assertEquals(ErrorCode.FILE_EMPTY, ex.getErrorCode());
    }

    @Test
    @DisplayName("不支持的类型抛 UNSUPPORTED_FILE_TYPE")
    void unsupportedType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse("app.exe", new byte[]{1, 2, 3}));
        assertEquals(ErrorCode.UNSUPPORTED_FILE_TYPE, ex.getErrorCode());
    }

    @Test
    @DisplayName("扩展名解析：大小写与无扩展名")
    void extensionParsing() {
        assertEquals("md", parser.extension("README.MD"));
        assertEquals("java", parser.extension("Demo.java"));
        assertEquals("", parser.extension("Makefile"));
        assertEquals("", parser.extension("noext."));
        assertFalse(parser.isCodeFile("README.md"));
    }

    /** 用 PDFBox 生成一份最小 PDF，用于验证 PDF 解析链路。 */
    private byte[] buildPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(80, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
