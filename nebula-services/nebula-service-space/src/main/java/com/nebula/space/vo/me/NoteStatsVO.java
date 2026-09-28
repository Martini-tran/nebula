package com.nebula.space.vo.me;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 随手记侧栏计数
 */
@Data
public class NoteStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 未归档
     */
    private long all;

    /**
     * 未归档且未置顶
     */
    private long temporary;

    /**
     * 未归档且置顶
     */
    private long pinned;

    private long archived;

    /**
     * 明天及以前到期的临时笔记
     */
    private long dueTomorrow;

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

        private long count;
    }
}
