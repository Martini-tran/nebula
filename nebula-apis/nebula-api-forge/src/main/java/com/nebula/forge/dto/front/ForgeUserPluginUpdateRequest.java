package com.nebula.forge.dto.front;

import lombok.Data;

/**
 * 我的插件更新请求（前台）
 * <p>更新启用状态、自动更新开关与可选同步的配置/窗口状态。</p>
 *
 * @author nebula
 */
@Data
public class ForgeUserPluginUpdateRequest {

    /**
     * 是否启用：1启用 0禁用
     */
    private Integer enabled;

    /**
     * 是否自动更新：1是 0否
     */
    private Integer autoUpdate;

    /**
     * 用户配置（JSON格式），可选同步
     */
    private String configJson;

    /**
     * 窗口状态（JSON格式），可选同步
     */
    private String windowStateJson;
}
