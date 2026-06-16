package com.nebula.forge.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 插件版本更新请求（选择性更新，非空字段才更新）
 *
 * @author nebula
 */
@Data
public class ForgePluginVersionUpdateRequest {

    /**
     * 版本号，semver
     */
    @Size(max = 50, message = "版本号长度不能超过50")
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
     * 插件包文件ID，关联 sys_file
     */
    private Long packageFileId;

    /**
     * 插件包外链，可选
     */
    private String packageUrl;

    /**
     * 插件包 SHA256
     */
    @Size(max = 64, message = "SHA256 长度不能超过64")
    private String packageSha256;

    /**
     * 包大小，字节
     */
    private Long packageSize;

    /**
     * 包签名，可选
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
     * 备注
     */
    private String remark;
}
