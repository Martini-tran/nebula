package com.nebula.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典数据表
 *
 * @author nebula
 */
@Data
@TableName("sys_dict_data")
public class SysDictData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 字典数据ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的字典类型ID
     */
    private Long dictTypeId;

    /**
     * 字典标签（展示值，如 "启用"）
     */
    private String dictLabel;

    /**
     * 字典值（实际存储值，如 "1"）
     */
    private String dictValue;

    /**
     * CSS类名（前端样式，如 text-success）
     */
    private String cssClass;

    /**
     * 列表样式（如 primary, danger）
     */
    private String listClass;

    /**
     * 是否默认：1=是
     */
    private Integer isDefault;

    /**
     * 排序（越小越靠前）
     */
    private Integer sort;

    /**
     * 状态：1=正常 0=停用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

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
}
