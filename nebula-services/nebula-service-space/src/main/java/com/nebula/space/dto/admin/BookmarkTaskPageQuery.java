package com.nebula.space.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台书签任务（导入/导出）分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookmarkTaskPageQuery extends PageQuery {

    /**
     * 用户ID（仅超级管理员可跨用户查询）
     */
    private Long userId;

    /**
     * 状态：0待处理 1处理中 2成功 3失败
     */
    private Integer status;
}
