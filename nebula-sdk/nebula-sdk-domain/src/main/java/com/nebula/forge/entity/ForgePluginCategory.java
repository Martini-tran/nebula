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
 * 插件分类表
 *
 * @author nebula
 */
@Data
@TableName("forge_plugin_category")
public class ForgePluginCategory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 插件分类ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 分类编码
     */
    private String code;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类说明
     */
    private String description;

    /**
     * 分类图标文件ID，关联 sys_file
     */
    private Long iconFileId;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态：1启用 0停用
     */
    private Integer status;

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
