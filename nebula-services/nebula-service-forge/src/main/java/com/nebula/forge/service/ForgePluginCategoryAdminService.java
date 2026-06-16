package com.nebula.forge.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.forge.dto.admin.ForgePluginCategoryCreateRequest;
import com.nebula.forge.dto.admin.ForgePluginCategoryPageQuery;
import com.nebula.forge.dto.admin.ForgePluginCategoryUpdateRequest;
import com.nebula.forge.vo.admin.ForgePluginCategoryAdminVO;

/**
 * 插件分类管理服务（管理员端）
 *
 * @author nebula
 */
public interface ForgePluginCategoryAdminService {

    /**
     * 分页查询插件分类
     */
    PageResult<ForgePluginCategoryAdminVO> page(ForgePluginCategoryPageQuery query);

    /**
     * 插件分类详情
     */
    ForgePluginCategoryAdminVO detail(Long id);

    /**
     * 创建插件分类
     */
    Long create(ForgePluginCategoryCreateRequest req);

    /**
     * 更新插件分类
     */
    void update(Long id, ForgePluginCategoryUpdateRequest req);

    /**
     * 切换分类启用/停用状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 删除插件分类
     */
    void delete(Long id);
}
