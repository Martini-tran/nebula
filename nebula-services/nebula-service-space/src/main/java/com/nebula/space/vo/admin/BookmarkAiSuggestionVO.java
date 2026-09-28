package com.nebula.space.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 一条书签的 AI 整理建议；只列出要改的项，不改的为空。前端让用户逐项勾选后用现有接口应用
 */
@Data
public class BookmarkAiSuggestionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 书签ID
     */
    private Long bookmarkId;

    /**
     * 建议移入的目录，不改为空
     */
    private FolderTarget folder;

    /**
     * 建议加上的标签（不含书签已有的），不改为空
     */
    private List<TagTarget> tags;

    /**
     * 建议的新标题，不改为空
     */
    private String title;

    /**
     * 建议的描述（只给原本没有描述的书签），不改为空
     */
    private String description;

    /**
     * 目标目录：已有目录带 id；id 为空表示要新建，path 为完整路径（「前端 / 工程化」），应用时逐级补建
     */
    @Data
    public static class FolderTarget implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long id;

        private String path;
    }

    /**
     * 标签：已有标签带 id 和颜色；id 为空表示要新建
     */
    @Data
    public static class TagTarget implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long id;

        private String name;

        private String color;
    }
}
