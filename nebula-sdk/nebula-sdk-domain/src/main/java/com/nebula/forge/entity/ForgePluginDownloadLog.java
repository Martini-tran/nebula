package com.nebula.forge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件下载日志表
 *
 * @author nebula
 */
@Data
@TableName("forge_plugin_download_log")
public class ForgePluginDownloadLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 插件下载日志ID
     */
    @TableId(value = "id", type = IdType.AUTO)
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
     * 插件版本ID
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
