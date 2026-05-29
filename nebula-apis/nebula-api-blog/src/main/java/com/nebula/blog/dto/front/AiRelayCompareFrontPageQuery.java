package com.nebula.blog.dto.front;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 中转额度比价（前台）查询参数
 *
 * <p>以 ai_relay_provider_package_limit 为主表的横向对比：每行=一条限额，附带所属
 * 套餐、服务商和（可选的）选定模型在该套餐下的输入 / 输出单价。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayCompareFrontPageQuery extends PageQuery {

    /**
     * 选定模型 ID。指定后会附带该模型在每个套餐下的输入/输出单价（含倍率），同时仅保留
     * 包含该模型的套餐对应的限额行；不传则展示所有套餐的限额，价格列为空。
     */
    private Long modelId;

    /**
     * 服务商 ID（可选筛选）
     */
    private Long providerId;

    /**
     * 套餐类型编码（day/week/month/usage 可选筛选）
     */
    private String packageTypeCode;

    /**
     * 限额类型（1总额 2每日 3每周 4每月 5单次，可选筛选）
     */
    private Integer limitType;

    /**
     * 排序键：input_price / output_price / quota / recommend，默认 recommend
     */
    private String sortBy;

    /**
     * 关键词（套餐名 / 服务商名）
     */
    private String keyword;
}
