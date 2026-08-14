package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI技能分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillPageQuery extends PageQuery {

    /**
     * 关键词（技能编码/名称/描述）
     */
    private String keyword;

    /**
     * 触发方式：AUTO/MANUAL
     */
    private String triggerType;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;
}
