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
 * 写作台章节表
 *
 * <p>归属校验走所属作品（scribe_work.user_id），本表不冗余 user_id。
 * 正文先按纯文本存储（段落以换行分隔），后续换 TipTap 时可无损转成段落节点。</p>
 *
 * @author nebula
 */
@Data
@TableName("scribe_chapter")
public class ScribeChapter implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 章节ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属作品ID
     */
    private Long workId;

    /**
     * 所属卷ID（卷表落地前恒为 null）
     */
    private Long volumeId;

    /**
     * 章节标题
     */
    private String title;

    /**
     * 排序值，按 1000 间隔稀疏分配，便于中间插入
     */
    private Integer sortOrder;

    /**
     * 状态：outline/drafting/revising/done
     */
    private String status;

    /**
     * 本章梗概
     */
    private String synopsis;

    /**
     * 正文，纯文本
     */
    private String content;

    /**
     * 字数：去掉空白后的字符数
     */
    private Integer wordCount;

    /**
     * 修订号，保存时比对，防止多处编辑互相覆盖
     */
    private Long revision;

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
