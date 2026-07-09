package com.nebula.forge.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 插件下载结果VO（前台）
 *
 * @author nebula
 */
@Data
public class ForgePluginDownloadResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 插件ID */
    private Long pluginId;

    /** 版本ID */
    private Long versionId;

    /** 版本号 */
    private String version;

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
}
