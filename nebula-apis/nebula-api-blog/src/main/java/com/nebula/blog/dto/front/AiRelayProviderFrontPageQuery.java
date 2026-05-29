package com.nebula.blog.dto.front;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * AI 中转服务商分页查询参数（前台）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayProviderFrontPageQuery extends PageQuery {

    /**
     * 关键词（服务商名称）
     */
    private String keyword;

    /**
     * 模型厂商筛选（claude / gpt / gemini ...），匹配服务商套餐绑定的模型 vendor
     */
    private String modelVendor;

    /**
     * 计费模式筛选（usage / subscription），匹配套餐类型的 billingMode
     */
    private String billingMode;

    /**
     * 套餐类型编码筛选（day / week / month / usage ...），匹配套餐对应类型 code
     */
    private String packageTypeCode;

    /**
     * 排序方式：recommend(默认) / price / stability
     */
    private String sortBy;

    /**
     * 同步时间起（含），格式 yyyy-MM-dd
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastSyncTimeStart;

    /**
     * 同步时间止（含），格式 yyyy-MM-dd
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastSyncTimeEnd;
}
