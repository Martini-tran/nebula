package com.nebula.forge.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.forge.dto.admin.ForgePluginCategoryBindRequest;
import com.nebula.forge.dto.admin.ForgePluginCreateRequest;
import com.nebula.forge.dto.admin.ForgePluginPageQuery;
import com.nebula.forge.dto.admin.ForgePluginUpdateRequest;
import com.nebula.forge.vo.admin.ForgePluginAdminVO;

/**
 * 插件管理服务（管理员端）
 *
 * @author nebula
 */
public interface ForgePluginAdminService {

    /**
     * 分页查询插件
     */
    PageResult<ForgePluginAdminVO> page(ForgePluginPageQuery query);

    /**
     * 插件详情（含关联分类）
     */
    ForgePluginAdminVO detail(Long id);

    /**
     * 创建插件
     */
    Long create(ForgePluginCreateRequest req);

    /**
     * 更新插件
     */
    void update(Long id, ForgePluginUpdateRequest req);

    /**
     * 更新插件状态：0草稿 1上架 2下架 3封禁
     */
    void updateStatus(Long id, Integer status);

    /**
     * 切换推荐标记：1是 0否
     */
    void updateFeatured(Long id, Integer isFeatured);

    /**
     * 全量重绑插件关联分类
     */
    void bindCategories(Long id, ForgePluginCategoryBindRequest req);

    /**
     * 删除插件
     */
    void delete(Long id);
}
