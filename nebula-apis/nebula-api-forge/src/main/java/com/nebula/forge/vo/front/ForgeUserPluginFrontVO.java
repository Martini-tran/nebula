package com.nebula.forge.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的插件VO（前台）
 * <p>包含插件概要 {@link ForgePluginFrontVO} 与当前用户的安装信息。</p>
 *
 * @author nebula
 */
@Data
public class ForgeUserPluginFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 插件概要（已上架时存在，可能为 null 表示插件已下架） */
    private ForgePluginFrontVO plugin;

    /** 插件ID */
    private Long pluginId;

    /** 当前安装版本ID */
    private Long versionId;

    /** 当前安装版本号 */
    private String installedVersion;

    /** 是否启用：1启用 0禁用 */
    private Integer enabled;

    /** 是否自动更新：1是 0否 */
    private Integer autoUpdate;

    /** 用户配置（JSON格式） */
    private String configJson;

    /** 窗口状态（JSON格式） */
    private String windowStateJson;

    /** 安装时间 */
    private LocalDateTime installTime;

    /** 最近使用时间 */
    private LocalDateTime lastUsedTime;
}
