package com.nebula.space.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件柜表（文件夹树与文件名在这里，文件内容走公共文件存储 sys_file）
 *
 * @author nebula
 */
@Data
@TableName("space_file")
public class SpaceFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 所在文件夹ID，空为根目录；移到根目录时要清空
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long folderId;

    /**
     * 是否文件夹：1 是 0 否
     */
    private Integer isFolder;

    /**
     * 名称
     */
    private String name;

    /**
     * 大小（字节），文件夹为 0
     */
    private Long sizeBytes;

    /**
     * MIME 类型，文件夹为空
     */
    private String mime;

    /**
     * 来源：upload / notes / meetings / reading / bookmarks
     */
    private String source;

    /**
     * 内容在公共文件表 sys_file 的ID，文件夹为空
     */
    private Long sysFileId;

    /**
     * 移进最近删除的时间，空为正常；恢复时要清空
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime deleteTime;

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
