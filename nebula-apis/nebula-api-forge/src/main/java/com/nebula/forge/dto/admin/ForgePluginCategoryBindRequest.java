package com.nebula.forge.dto.admin;

import lombok.Data;

import java.util.List;

/**
 * 插件分类绑定请求（全量重绑）
 *
 * @author nebula
 */
@Data
public class ForgePluginCategoryBindRequest {

    /**
     * 关联分类ID列表，传空列表表示清空关联
     */
    private List<Long> categoryIds;
}
