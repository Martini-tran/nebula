package com.nebula.forge.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 插件版本分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForgePluginVersionPageQuery extends PageQuery {

    /**
     * 状态：0草稿 1已发布 2已下架 3废弃
     */
    private Integer status;

    /**
     * 审核状态：0待审 1通过 2拒绝
     */
    private Integer reviewStatus;

    /**
     * 发布通道：stable/beta/dev
     */
    private String channel;
}
