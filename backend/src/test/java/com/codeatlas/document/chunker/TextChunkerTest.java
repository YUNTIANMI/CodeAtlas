package com.codeatlas.document.chunker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本切分器测试。
 */
class TextChunkerTest {

    private TextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new TextChunker();
    }

    @Test
    @DisplayName("空文本返回空列表")
    void emptyText() {
        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk("   ").isEmpty());
    }

    @Test
    @DisplayName("短于阈值时返回单个 Chunk")
    void shortText() {
        List<String> chunks = chunker.chunk("这是一段很短的文本");

        assertEquals(1, chunks.size());
        assertEquals("这是一段很短的文本", chunks.get(0));
    }

    @Test
    @DisplayName("长文本切分为多个 Chunk")
    void longText() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            text.append("这是第 ").append(i).append(" 行内容，用于测试切分效果。\n");
        }

        List<String> chunks = chunker.chunk(text.toString());

        assertTrue(chunks.size() > 1, "长文本应切分为多块，实际 " + chunks.size());
        chunks.forEach(chunk -> assertTrue(chunk.length() <= TextChunker.DEFAULT_CHUNK_SIZE + 100,
                "单个 Chunk 不应显著超过阈值"));
    }

    @Test
    @DisplayName("切分不丢失内容：所有 Chunk 拼接后包含首尾内容")
    void noContentLoss() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            text.append("行 ").append(i).append(" 的内容\n");
        }

        List<String> chunks = chunker.chunk(text.toString());
        String joined = String.join("", chunks);

        assertTrue(joined.contains("行 0 的内容"), "应包含开头内容");
        assertTrue(joined.contains("行 199 的内容"), "应包含结尾内容");
    }

    @Test
    @DisplayName("相邻 Chunk 存在重叠")
    void overlapExists() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            text.append("句子编号 ").append(i).append("，这是一段用于验证重叠的填充文本。\n");
        }

        List<String> chunks = chunker.chunk(text.toString());

        assertTrue(chunks.size() >= 2);
        // 重叠意味着相邻块之间不会完全割裂：第二块的开头不会恰好是第一块结尾的下一句
        assertFalse(chunks.get(0).endsWith(chunks.get(1)));
    }

    @Test
    @DisplayName("Chunk 大小为 0 时抛出参数异常")
    void invalidChunkSize() {
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk("abc", 0, 0));
    }

    @Test
    @DisplayName("不产生空 Chunk")
    void noEmptyChunks() {
        String text = "第一行\n\n\n\n第二行\n" + "空行很多的文本\n\n\n" + "结尾\n";

        List<String> chunks = chunker.chunk(text, 50, 10);

        assertFalse(chunks.isEmpty());
        chunks.forEach(chunk -> assertFalse(chunk.isBlank(), "不应产生空白 Chunk"));
    }
}
