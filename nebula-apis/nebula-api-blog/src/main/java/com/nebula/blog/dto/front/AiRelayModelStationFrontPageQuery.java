package com.nebula.blog.dto.front;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * AI 中转「模型选择站点」（前台）查询参数
 *
 * <p>以 ai_relay_package_model 为主表：每行=一条「套餐 × 模型」绑定，附带所属套餐、服务商（主站），
 * 以及该绑定的输入 / 输出每百万 token 单价、消耗倍率与实付单价。先选定模型，再横向对比支持该模型的
 * 各主站套餐。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiRelayModelStationFrontPageQuery extends PageQuery {

    /**
     * 选定模型 ID。必填——本页面以「先选模型，再看站点」为核心；不传则返回空结果。
     */
    private Long modelId;

    /**
     * 服务商（主站）ID 多选筛选。为空表示不限。
     */
    private List<Long> providerIds;

    /**
     * 单选服务商（主站）ID（向后兼容）。当 providerIds 为空时生效。
     */
    private Long providerId;

    /**
     * 套餐类型编码（day/week/month/usage 可选筛选）
     */
    private String packageTypeCode;

    /**
     * 排序键：input_price（实付输入价升序）/ output_price（实付输出价升序）/ multiplier（倍率升序）
     * / context（上下文降序）/ recommend（默认，推荐优先），默认 recommend
     */
    private String sortBy;

    /**
     * 关键词（套餐名 / 服务商名）
     */
    private String keyword;
}
