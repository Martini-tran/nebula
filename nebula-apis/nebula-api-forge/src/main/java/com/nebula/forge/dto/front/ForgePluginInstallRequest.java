package com.nebula.forge.dto.front;

import lombok.Data;

/**
 * 安装上报请求（前台）
 * <p>客户端安装某插件版本后上报，用于「我的插件」与安装量统计。</p>
 *
 * @author nebula
 */
@Data
public class ForgePluginInstallRequest {

    /**
     * 安装的版本ID
     */
    private Long versionId;

    /**
     * 安装的版本号
     */
    private String installedVersion;

    /**
     * 是否自动更新：1是 0否，缺省1
     */
    private Integer autoUpdate;
}
