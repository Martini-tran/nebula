package com.nebula.space.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台书签导出任务视图对象
 */
@Data
public class BookmarkExportTaskAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 导出文件ID
     */
    private Long fileId;

    /**
     * 导出类型：chrome_html/json
     */
    private String exportType;

    /**
     * 范围：all/folder/tag
     */
    private String scopeType;

    /**
     * 范围ID
     */
    private Long scopeId;

    /**
     * 状态：0待处理 1处理中 2成功 3失败
     */
    private Integer status;

    /**
     * 导出数量
     */
    private Integer totalCount;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
