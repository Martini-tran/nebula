package com.nebula.forge.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件下载日志VO（管理员端）
 *
 * @author nebula
 */
@Data
public class ForgePluginDownloadLogAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    private Long id;

    /**
     * 用户ID，匿名下载可为空
     */
    private Long userId;

    /**
     * 插件ID
     */
    private Long pluginId;

    /**
     * 版本ID
     */
    private Long versionId;

    /**
     * 客户端版本
     */
    private String clientVersion;

    /**
     * 客户端系统
     */
    private String clientOs;

    /**
     * 客户端IP
     */
    private String ip;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 结果：1成功 0失败
     */
    private Integer result;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
