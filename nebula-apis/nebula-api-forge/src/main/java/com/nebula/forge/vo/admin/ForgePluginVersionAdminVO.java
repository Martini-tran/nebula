package com.nebula.forge.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件版本管理VO（管理员端）
 *
 * @author nebula
 */
@Data
public class ForgePluginVersionAdminVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本ID
     */
    private Long id;

    /**
     * 插件ID
     */
    private Long pluginId;

    /**
     * 版本号
     */
    private String version;

    /**
     * 发布通道：stable/beta/dev
     */
    private String channel;

    /**
     * plugin.json 快照（JSON 字符串）
     */
    private String manifestJson;

    /**
     * 插件包文件ID
     */
    private Long packageFileId;

    /**
     * 插件包外链
     */
    private String packageUrl;

    /**
     * 插件包 SHA256
     */
    private String packageSha256;

    /**
     * 包大小，字节
     */
    private Long packageSize;

    /**
     * 包签名
     */
    private String signature;

    /**
     * 最低宿主版本
     */
    private String minAppVersion;

    /**
     * 最高宿主版本
     */
    private String maxAppVersion;

    /**
     * 更新日志
     */
    private String changelog;

    /**
     * 当前版本下载次数
     */
    private Long downloadCount;

    /**
     * 审核状态：0待审 1通过 2拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核备注
     */
    private String reviewRemark;

    /**
     * 发布时间
     */
    private LocalDateTime publishedTime;

    /**
     * 状态：0草稿 1已发布 2已下架 3废弃
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
