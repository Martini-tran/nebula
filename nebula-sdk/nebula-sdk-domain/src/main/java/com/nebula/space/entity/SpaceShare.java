package com.nebula.space.entity;

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
 * 文件分享链接表（下载经服务端校验提取码、有效期、次数后再给内容）
 *
 * @author nebula
 */
@Data
@TableName("space_share")
public class SpaceShare implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分享ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 分享人ID
     */
    private Long userId;

    /**
     * 短码，链接为 /s/{code}
     */
    private String code;

    /**
     * 分享的文件或文件夹 space_file.id
     */
    private Long fileId;

    /**
     * 分享时的名称快照
     */
    private String fileName;

    /**
     * 是否文件夹：1 是 0 否
     */
    private Integer isFolder;

    /**
     * 提取码，空为不需要
     */
    private String password;

    /**
     * 过期时间，空为永久
     */
    private LocalDateTime expireAt;

    /**
     * 最多下载次数，空为不限
     */
    private Integer maxDownloads;

    /**
     * 已下载次数
     */
    private Integer downloads;

    /**
     * 是否已被分享人取消：1 是 0 否
     */
    private Integer revoked;

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
