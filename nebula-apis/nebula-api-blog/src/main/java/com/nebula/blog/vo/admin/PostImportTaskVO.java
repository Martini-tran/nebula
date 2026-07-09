package com.nebula.blog.vo.admin;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章批量导入任务 VO（汇总）。
 * <p>列表接口不填充 {@link #items}，详情接口填充逐文件明细。
 */
@Data
public class PostImportTaskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private Long id;

    /**
     * 发起导入的用户ID
     */
    private Long userId;

    /**
     * 状态：pending(等待)/running(执行中)/success(完成)/failed(失败)
     */
    private String status;

    /**
     * 文件总数
     */
    private Integer totalCount;

    /**
     * 已处理数（进度）
     */
    private Integer processedCount;

    /**
     * 成功数
     */
    private Integer successCount;

    /**
     * 失败数
     */
    private Integer failCount;

    /**
     * 统一应用的文章状态
     */
    private String postStatus;

    /**
     * 统一应用的可见性
     */
    private String visibility;

    /**
     * 统一应用的内容类型
     */
    private String postType;

    /**
     * 是否转存外链图片
     */
    private Boolean rehostImages;

    /**
     * 任务级错误信息（整体失败时记录）
     */
    private String errorMessage;

    /**
     * 开始处理时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime finishedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 逐文件明细（仅详情接口填充）
     */
    private List<PostImportItemVO> items;
}
