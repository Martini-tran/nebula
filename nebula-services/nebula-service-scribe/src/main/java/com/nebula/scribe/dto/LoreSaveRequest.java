package com.nebula.scribe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建/修改设定条目请求，字段对齐前端 {@code LoreSaveRequest}；修改为整表单覆盖
 */
@Data
public class LoreSaveRequest {

    /**
     * 类型：character/location/faction/item/rule
     */
    @NotBlank(message = "设定类型不能为空")
    private String kind;

    /**
     * 名称，同一作品内不重复
     */
    @NotBlank(message = "设定名称不能为空")
    @Size(max = 100, message = "设定名称长度不能超过100")
    private String name;

    /**
     * 别名
     */
    @Size(max = 10, message = "别名最多10个")
    private List<@Size(max = 30, message = "单个别名长度不能超过30") String> aliases;

    /**
     * 一句话概述
     */
    @Size(max = 300, message = "概述长度不能超过300")
    private String summary;

    /**
     * 详细设定，Markdown
     */
    @Size(max = 20_000, message = "详细设定长度不能超过20000")
    private String detail;

    /**
     * 标签
     */
    @Size(max = 10, message = "标签最多10个")
    private List<@Size(max = 20, message = "单个标签长度不能超过20") String> tags;

    /**
     * AI 生成时是否默认带上，不传按否
     */
    private Boolean pinned;
}
