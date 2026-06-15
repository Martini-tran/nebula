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
 * 插件评价表
 *
 * @author nebula
 */
@Data
@TableName("forge_plugin_review")
public class ForgePluginReview implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 插件评价ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 插件ID
     */
    private Long pluginId;

    /**
     * 评价时安装的版本ID
     */
    private Long versionId;

    /**
     * 评价用户ID
     */
    private Long userId;

    /**
     * 评分：1-5
     */
    private Integer rating;

    /**
     * 评价内容
     */
    private String content;

    /**
     * 作者/管理员回复
     */
    private String replyContent;

    /**
     * 回复人ID
     */
    private Long replyBy;

    /**
     * 回复时间
     */
    private LocalDateTime replyTime;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 状态：0待审 1展示 2隐藏 3拒绝
     */
    private Integer status;

    /**
     * 审核备注
     */
    private String auditRemark;

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
