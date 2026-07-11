package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI提示词分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromptPageQuery extends PageQuery {

    /**
     * 关键词（提示词编码/名称/正文）
     */
    private String keyword;

    /**
     * 消息角色：system/user/assistant
     */
    private String role;
}
