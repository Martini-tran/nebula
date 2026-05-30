package com.nebula.blog.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * AI中转服务商充值记录分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayProviderRechargePageQuery extends PageQuery {

    /**
     * 服务商ID
     */
    private Long providerId;

    /**
     * 套餐ID
     */
    private Long packageId;

    /**
     * 状态（1正常 0作废）
     */
    private Integer status;

    /**
     * 起始时间（充值时间 >=）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 结束时间（充值时间 <=）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
