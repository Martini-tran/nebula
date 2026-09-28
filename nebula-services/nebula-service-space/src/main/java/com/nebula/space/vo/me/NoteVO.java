package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 随手记视图对象
 */
@Data
public class NoteVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * Markdown 正文
     */
    private String content;

    /**
     * 便签底色
     */
    private String color;

    /**
     * 置顶 = 长期笔记
     */
    private Boolean pinned;

    /**
     * 临时笔记到期日，长期笔记为空
     */
    private LocalDate expireDate;

    private Boolean archived;

    private List<String> tags;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
