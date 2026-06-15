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
 * 插件分类关联表
 *
 * @author nebula
 */
@Data
@TableName("forge_plugin_category_rel")
public class ForgePluginCategoryRel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 插件分类关联ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 插件ID
     */
    private Long pluginId;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
