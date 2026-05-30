package com.nebula.space.entity;

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
 * 书签导出任务表
 *
 * @author nebula
 */
@Data
@TableName("space_bookmark_export_task")
public class SpaceBookmarkExportTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导出任务ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 导出文件ID，关联sys_file
     */
    private Long fileId;

    /**
     * 导出类型：chrome_html/json
     */
    private String exportType;

    /**
     * 范围：all/folder/tag
     */
    private String scopeType;

    /**
     * 范围ID
     */
    private Long scopeId;

    /**
     * 状态：0待处理 1处理中 2成功 3失败
     */
    private Integer status;

    /**
     * 导出数量
     */
    private Integer totalCount;

    /**
     * 错误信息
     */
    private String errorMsg;

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
