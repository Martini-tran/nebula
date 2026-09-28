package com.nebula.space.dto.me;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 随手记新建 / 局部保存请求：字段为空表示不修改
 */
@Data
public class NoteSaveRequest {

    /**
     * Markdown 正文
     */
    @Size(max = 100000, message = "正文过长")
    private String content;

    /**
     * 便签底色
     */
    @Pattern(regexp = "plain|yellow|green|blue|pink|purple", message = "不支持的底色")
    private String color;

    /**
     * 置顶 = 长期笔记
     */
    private Boolean pinned;

    /**
     * 标签名
     */
    @Size(max = 20, message = "标签最多 20 个")
    private List<@Size(max = 30, message = "标签名最长 30 字") String> tags;

    /**
     * 归档 / 从归档恢复
     */
    private Boolean archived;

    /**
     * 手动指定到期日：笔记变为临时笔记并在这天之后归档
     */
    @FutureOrPresent(message = "到期日不能早于今天")
    private LocalDate expireDate;

    /**
     * 临时笔记寿命（天），取自用户偏好；0 表示新笔记默认长期。偏好还在前端，由前端带上
     */
    @Min(0)
    @Max(365)
    private Integer ttlDays;
}
