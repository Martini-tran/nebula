package com.nebula.space.vo.me;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 随手记侧栏计数
 *
 * <p>计数用 int：全局把 Long 序列化成字符串（防雪花 ID 丢精度），"0" 在前端是真值，会让「0 条到期」这类提示冒出来。</p>
 */
@Data
public class NoteStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 未归档
     */
    private int all;

    /**
     * 未归档且未置顶
     */
    private int temporary;

    /**
     * 未归档且置顶
     */
    private int pinned;

    private int archived;

    /**
     * 明天及以前到期的临时笔记
     */
    private int dueTomorrow;

    /**
     * 未归档笔记的标签计数，按数量倒序
     */
    private List<TagCount> tags;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagCount implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String name;

        private int count;
    }
}
