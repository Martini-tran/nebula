package com.nebula.forge.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.forge.dto.admin.ForgePluginPermissionBindRequest;
import com.nebula.forge.dto.admin.ForgePluginVersionCreateRequest;
import com.nebula.forge.dto.admin.ForgePluginVersionPageQuery;
import com.nebula.forge.dto.admin.ForgePluginVersionReviewRequest;
import com.nebula.forge.dto.admin.ForgePluginVersionUpdateRequest;
import com.nebula.forge.vo.admin.ForgePluginPermissionAdminVO;
import com.nebula.forge.vo.admin.ForgePluginVersionAdminVO;

import java.util.List;

/**
 * 插件版本管理服务（管理员端）
 *
 * @author nebula
 */
public interface ForgePluginVersionAdminService {

    /**
     * 分页查询某插件的版本
     */
    PageResult<ForgePluginVersionAdminVO> page(Long pluginId, ForgePluginVersionPageQuery query);

    /**
     * 版本详情
     */
    ForgePluginVersionAdminVO detail(Long pluginId, Long versionId);

    /**
     * 创建版本
     */
    Long create(Long pluginId, ForgePluginVersionCreateRequest req);

    /**
     * 更新版本
     */
    void update(Long pluginId, Long versionId, ForgePluginVersionUpdateRequest req);

    /**
     * 审核版本：1通过 2拒绝
     */
    void review(Long pluginId, Long versionId, ForgePluginVersionReviewRequest req);

    /**
     * 更新版本状态：0草稿 1已发布 2已下架 3废弃
     */
    void updateStatus(Long pluginId, Long versionId, Integer status);

    /**
     * 删除版本
     */
    void delete(Long pluginId, Long versionId);

    /**
     * 查询版本权限声明
     */
    List<ForgePluginPermissionAdminVO> listPermissions(Long pluginId, Long versionId);

    /**
     * 全量替换版本权限声明
     */
    void bindPermissions(Long pluginId, Long versionId, ForgePluginPermissionBindRequest req);
}
