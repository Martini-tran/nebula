package com.nebula.space.dto.me;

import lombok.Data;

/**
 * 随手记列表查询
 */
@Data
public class NoteQuery {

    /**
     * 视图：all（默认，未归档）/ temporary（未置顶）/ pinned（置顶）/ archived（已归档）
     */
    private String view;

    /**
     * 按标签名筛选
     */
    private String tag;

    /**
     * 关键词：匹配正文与标签
     */
    private String keyword;
}
