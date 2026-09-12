package com.codeatlas.document.chunker;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切分器：将解析后的文本切分为 Chunk，供 Phase 5 做 Embedding。
 *
 * <p>切分策略：优先在换行处断开，避免把句子或代码行切断；
 * 相邻 Chunk 之间保留重叠，降低语义被截断的风险。
 */
@Component
public class TextChunker {

    public static final int DEFAULT_CHUNK_SIZE = 1000;

    public static final int DEFAULT_OVERLAP = 200;

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必须大于 0");
        }
        int safeOverlap = Math.max(0, Math.min(overlap, chunkSize / 2));

        String normalized = text.replace("\r\n", "\n");
        if (normalized.length() <= chunkSize) {
            String trimmed = normalized.trim();
            return trimmed.isEmpty() ? List.of() : List.of(trimmed);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());

            // 未到末尾时，尽量在换行处断开，保持语义完整
            if (end < normalized.length()) {
                int breakAt = normalized.lastIndexOf('\n', end);
                if (breakAt > start + chunkSize / 2) {
                    end = breakAt + 1;
                }
            }

            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }

            if (end >= normalized.length()) {
                break;
            }
            // 保证指针始终前进，避免死循环
            start = Math.max(end - safeOverlap, start + 1);
        }
        return chunks;
    }
}
