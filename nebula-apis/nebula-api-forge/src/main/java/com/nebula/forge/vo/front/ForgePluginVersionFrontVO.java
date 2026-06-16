package com.nebula.forge.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件版本VO（前台）
 *
 * @author nebula
 */
@Data
public class ForgePluginVersionFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long id;

    /** 插件ID */
    private Long pluginId;

    /** 版本号 */
    private String version;

    /** 发布通道：stable/beta/dev */
    private String channel;

    /** 插件清单（plugin.json 内容） */
    private String manifestJson;

    /** 安装包文件ID */
    private Long packageFileId;

    /** 安装包下载地址 */
    private String packageUrl;

    /** 安装包 SHA256 */
    private String packageSha256;

    /** 安装包大小（字节） */
    private Long packageSize;

    /** 安装包签名 */
    private String signature;

    /** 最低应用版本 */
    private String minAppVersion;

    /** 最高应用版本 */
    private String maxAppVersion;

    /** 更新日志 */
    private String changelog;

    /** 下载次数 */
    private Long downloadCount;

    /** 发布时间 */
    private LocalDateTime publishedTime;
}
