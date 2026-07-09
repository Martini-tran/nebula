package com.nebula.forge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 插件版本表
 *
 * @author nebula
 */
@Data
@TableName("forge_plugin_version")
public class ForgePluginVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 插件版本ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 插件ID
     */
    private Long pluginId;

    /**
     * 版本号，semver
     */
    private String version;

    /**
     * 发布通道：stable/beta/dev
     */
    private String channel;

    /**
     * plugin.json 快照（JSON格式）
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
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0否 1是
     */
    @TableLogic
    private Integer deleted;

    /**
     * 删除时间
     */
    private LocalDateTime deleteTime;
}
