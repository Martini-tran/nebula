package com.nebula.forge.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 插件版本创建请求
 *
 * @author nebula
 */
@Data
public class ForgePluginVersionCreateRequest {

    /**
     * 版本号，semver
     */
    @NotBlank(message = "版本号不能为空")
    @Size(max = 50, message = "版本号长度不能超过50")
    private String version;

    /**
     * 发布通道：stable/beta/dev
     */
    private String channel = "stable";

    /**
     * plugin.json 快照（JSON 字符串）
     */
    @NotBlank(message = "manifest 不能为空")
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
    @NotBlank(message = "插件包 SHA256 不能为空")
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
     * 状态：0草稿 1已发布 2已下架 3废弃
     */
    private Integer status = 0;

    /**
     * 备注
     */
    private String remark;
}
