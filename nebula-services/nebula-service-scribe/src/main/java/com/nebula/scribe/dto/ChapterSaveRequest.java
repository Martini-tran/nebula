package com.nebula.scribe.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存章节请求，字段对齐前端 {@code ChapterSaveRequest}
 *
 * <p>局部更新语义：未传（null）的字段保持不变，自动保存只带正文即可；
 * 传空串表示清空（标题除外，标题不能为空）。</p>
 */
@Data
public class ChapterSaveRequest {

    /**
     * 章节标题
     */
    @Size(max = 100, message = "章节标题长度不能超过100")
    private String title;

    /**
     * 本章梗概
     */
    @Size(max = 1000, message = "梗概长度不能超过1000")
    private String synopsis;

    /**
     * 状态：outline/drafting/revising/done
     */
    private String status;

    /**
     * 正文，纯文本
     */
    @Size(max = 200_000, message = "单章正文过长，请拆分章节")
    private String content;

    /**
     * 客户端持有的修订号，与库里不一致说明此章已在别处修改
     */
    @NotNull(message = "修订号不能为空")
    private Long revision;
}
