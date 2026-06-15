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
 * 用户插件安装表
 *
 * @author nebula
 */
@Data
@TableName("forge_user_plugin")
public class ForgeUserPlugin implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户插件ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 插件ID
     */
    private Long pluginId;

    /**
     * 当前安装版本ID
     */
    private Long versionId;

    /**
     * 当前安装版本号
     */
    private String installedVersion;

    /**
     * 是否启用：1启用 0禁用
     */
    private Integer enabled;

    /**
     * 是否自动更新：1是 0否
     */
    private Integer autoUpdate;

    /**
     * 用户配置，可选同步（JSON格式）
     */
    private String configJson;

    /**
     * 窗口状态，可选同步（JSON格式）
     */
    private String windowStateJson;

    /**
     * 安装时间
     */
    private LocalDateTime installTime;

    /**
     * 最近使用时间
     */
    private LocalDateTime lastUsedTime;

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
}
