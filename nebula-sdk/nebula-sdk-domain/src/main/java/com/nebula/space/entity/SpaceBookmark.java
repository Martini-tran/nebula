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
 * 书签表
 *
 * @author nebula
 */
@Data
@TableName("space_bookmark")
public class SpaceBookmark implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 书签ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 所属目录ID，0表示未分类
     */
    private Long folderId;

    /**
     * 书签标题
     */
    private String title;

    /**
     * 原始URL
     */
    private String url;

    /**
     * 规范化URL，用于去重
     */
    private String normalizedUrl;

    /**
     * URL哈希，用于索引和去重
     */
    private String urlHash;

    /**
     * 域名
     */
    private String domain;

    /**
     * 描述
     */
    private String description;

    /**
     * 站点图标URL
     */
    private String faviconUrl;

    /**
     * 站点图标文件ID，关联sys_file
     */
    private Long faviconFileId;

    /**
     * 来源：manual/chrome/import
     */
    private String source;

    /**
     * 外部来源标识
     */
    private String sourceKey;

    /**
     * 状态：0正常 1归档 2失效
     */
    private Integer status;

    /**
     * 访问次数
     */
    private Integer visitCount;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastVisitTime;

    /**
     * 目录内排序
     */
    private Integer sortOrder;

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
