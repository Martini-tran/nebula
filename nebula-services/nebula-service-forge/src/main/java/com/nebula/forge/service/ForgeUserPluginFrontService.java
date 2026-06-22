package com.nebula.forge.service;

import com.nebula.common.core.domain.PageQuery;
import com.nebula.common.core.domain.PageResult;
import com.nebula.forge.dto.front.ForgePluginInstallRequest;
import com.nebula.forge.dto.front.ForgeUserPluginUpdateRequest;
import com.nebula.forge.vo.front.ForgeUserPluginFrontVO;

/**
 * 我的插件 / 安装上报前台服务（需登录）
 *
 * @author nebula
 */
public interface ForgeUserPluginFrontService {

    /**
     * 安装上报（按 用户+插件 唯一，存在则更新版本信息）。
     * <p>首次安装时累加插件安装量。</p>
     */
    void install(Long pluginId, ForgePluginInstallRequest req);

    /**
     * 卸载上报（移除「我的插件」记录）。
     */
    void uninstall(Long pluginId);

    /**
     * 我的插件列表（分页）。
     */
    PageResult<ForgeUserPluginFrontVO> myPlugins(PageQuery query);

    /**
     * 更新我的插件（启用/自动更新/配置/窗口状态）。
     */
    void update(Long pluginId, ForgeUserPluginUpdateRequest req);

    /**
     * 更新最近使用时间。
     */
    void touchUsed(Long pluginId);
}
