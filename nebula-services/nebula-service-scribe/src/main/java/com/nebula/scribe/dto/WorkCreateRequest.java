package com.nebula.scribe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建作品请求，字段对齐前端 {@code WorkCreateRequest}
 */
@Data
public class WorkCreateRequest {

    /**
     * 作品标题
     */
    @NotBlank(message = "作品标题不能为空")
    @Size(max = 100, message = "作品标题长度不能超过100")
    private String title;

    /**
     * 一句话简介
     */
    @Size(max = 200, message = "一句话简介长度不能超过200")
    private String summary;

    /**
     * 一句话立意/核心冲突
     */
    @Size(max = 300, message = "立意长度不能超过300")
    private String logline;

    /**
     * 作品简介，纯文本
     */
    @Size(max = 5000, message = "作品简介长度不能超过5000")
    private String intro;

    /**
     * 目标读者：male/female/general
     */
    private String audience;

    /**
     * 题材
     */
    @Size(max = 32, message = "题材长度不能超过32")
    private String genre;

    /**
     * 标签
     */
    @Size(max = 10, message = "标签最多10个")
    private List<@NotBlank @Size(max = 20, message = "单个标签长度不能超过20") String> tags;

    /**
     * 主角名
     */
    @Size(max = 5, message = "主角最多5个")
    private List<@NotBlank @Size(max = 20, message = "主角名长度不能超过20") String> protagonists;

    /**
     * 目标总字数
     */
    @Positive(message = "目标字数必须大于0")
    @Max(value = 100_000_000, message = "目标字数过大")
    private Integer targetWordCount;
}
