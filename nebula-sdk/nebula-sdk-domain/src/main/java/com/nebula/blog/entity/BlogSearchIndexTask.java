package com.nebula.blog.entity;

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
 * 博客搜索索引同步任务表
 *
 * @author nebula
 */
@Data
@TableName("blog_search_index_task")
public class BlogSearchIndexTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的文章ID
     */
    private Long postId;

    /**
     * 索引名称
     */
    private String indexName;

    /**
     * 操作类型：upsert(插入/更新)/delete(删除)
     */
    private String action;

    /**
     * 状态：pending(等待)/running(执行中)/success(成功)/failed(失败)
     */
    private String status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 错误信息（失败时记录）
     */
    private String errorMessage;

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
     * 完成时间
     */
    private LocalDateTime finishedAt;
}
