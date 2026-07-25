package com.nebula.common.ai.rag.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块器（字符窗口 + 重叠）。
 *
 * <p>按固定字符窗口切分正文，相邻窗口保留一段重叠以避免边界语义割裂。窗口大小与重叠可配。切块是入库前的必要步骤：
 * 长文档整体 embed 会稀释语义、超模型上下文，切成语义相对完整的小块后逐块向量化，检索粒度更细、召回更准。
 *
 * @author nebula
 */
public final class TextChunker {

    private TextChunker() {
    }

    /**
     * 切块。
     *
     * @param text        正文
     * @param windowSize  窗口大小（字符数，&gt;0）
     * @param overlap     重叠字符数（&gt;=0 且 &lt;windowSize）
     * @return 切片正文列表（顺序即 chunkIndex）
     */
    public static List<String> chunk(String text, int windowSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int window = Math.max(1, windowSize);
        int step = Math.max(1, window - Math.max(0, Math.min(overlap, window - 1)));
        int length = text.length();
        for (int start = 0; start < length; start += step) {
            int end = Math.min(start + window, length);
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= length) {
                break;
            }
        }
        return chunks;
    }
}
