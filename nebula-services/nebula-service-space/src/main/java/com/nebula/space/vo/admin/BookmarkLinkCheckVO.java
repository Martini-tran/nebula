package com.nebula.space.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一条书签的链接检查结果
 */
@Data
public class BookmarkLinkCheckVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 书签ID
     */
    private Long id;

    /**
     * 结论：alive 能打开 / dead 打不开 / unknown 无法确定 / skipped 未检查（已归档）
     */
    private String verdict;

    /**
     * 打不开或无法确定的原因
     */
    private String reason;

    /**
     * 检查后的书签状态：0正常 1归档 2失效
     */
    private Integer status;

    /**
     * 状态是否因这次检查而改变
     */
    private Boolean changed;
}
