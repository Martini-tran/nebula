package com.nebula.blog.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台文章导入任务分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostImportTaskPageQuery extends PageQuery {

    /**
     * 状态：pending/running/success/failed
     */
    private String status;

    /**
     * 发起导入的用户ID
     */
    private Long userId;
}
