package com.nebula.scribe.entity;

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
 * 写作台作品表
 *
 * <p>只存与第三方平台无关的作品本体信息；各平台的书名/简介/分类等差异见保留表 scribe_work_platform。</p>
 *
 * @author nebula
 */
@Data
@TableName("scribe_work")
public class ScribeWork implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作品ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属作者ID（关联sys_user）
     */
    private Long userId;

    /**
     * 作品标题
     */
    private String title;

    /**
     * 一句话简介（列表卡片展示）
     */
    private String summary;

    /**
     * 一句话立意/核心冲突（AI上下文用，不对外）
     */
    private String logline;

    /**
     * 作品简介，纯文本
     */
    private String intro;

    /**
     * 目标读者：male/female/general
     */
    private String audience;

    /**
     * 题材（作者自定义）
     */
    private String genre;

    /**
     * 标签数组，JSON 文本
     */
    private String tags;

    /**
     * 主角名数组，JSON 文本
     */
    private String protagonists;

    /**
     * 封面文件ID（关联sys_file）
     */
    private Long coverFileId;

    /**
     * 状态：draft/serializing/paused/finished
     */
    private String status;

    /**
     * 目标总字数
     */
    private Integer targetWordCount;

    /**
     * 累计字数（章节保存时冗余维护）
     */
    private Integer wordCount;

    /**
     * 章节数（冗余维护）
     */
    private Integer chapterCount;

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
