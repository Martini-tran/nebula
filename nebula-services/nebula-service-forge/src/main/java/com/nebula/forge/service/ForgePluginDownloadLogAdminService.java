package com.nebula.forge.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.forge.dto.admin.ForgePluginDownloadLogPageQuery;
import com.nebula.forge.vo.admin.ForgePluginDownloadLogAdminVO;

/**
 * 插件下载日志查询服务（管理员端，只读）
 *
 * @author nebula
 */
public interface ForgePluginDownloadLogAdminService {

    /**
     * 分页查询下载日志
     */
    PageResult<ForgePluginDownloadLogAdminVO> page(ForgePluginDownloadLogPageQuery query);
}
