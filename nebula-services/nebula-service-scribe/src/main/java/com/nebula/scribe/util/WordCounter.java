package com.nebula.scribe.util;

/**
 * 字数统计
 *
 * <p>口径：去掉空白后的字符数——汉字、标点、英文字母、数字各算 1 个，空格与换行不算，
 * 与起点/番茄作者后台的显示大致一致。按码点计数，生僻字与 emoji 不会被算成 2 个。</p>
 */
public final class WordCounter {

    private WordCounter() {
    }

    public static int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) text.codePoints()
                // 全角空格 U+3000 也属于空白；isWhitespace 不认它，isSpaceChar 补上
                .filter(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp))
                .count();
    }
}
