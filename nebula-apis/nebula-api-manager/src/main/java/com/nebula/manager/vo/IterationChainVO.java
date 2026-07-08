package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 跨实例迭代链视图
 * 链头字段 + 已产出实例时间线（回溯 last_instance_id 链，用于"看这个系列写到第几篇、每篇是哪个实例"）。
 *
 * @author nebula
 */
@Data
public class IterationChainVO {

    private Long id;

    /**
     * 链唯一标识
     */
    private String chainId;

    private String name;

    private String agentCode;

    private String cron;

    private LocalDateTime nextRunAt;

    /**
     * 已完成轮次（= 已写到第几篇）
     */
    private Integer seq;

    private Integer maxIterations;

    private String untilExpr;

    /**
     * carry-over 映射
     */
    private Map<String, String> carryOver;

    /**
     * 首轮种子入参
     */
    private Map<String, Object> seedInputs;

    /**
     * 上一轮实例 id
     */
    private String lastInstanceId;

    private String status;

    private Integer consecutiveFails;

    private String errorMsg;

    /**
     * 每轮 advance 成功后回调的 URL（如 blog 落库接口）
     */
    private String webhookUrl;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 已产出实例时间线（详情才带；列表不带）——按轮次顺序的实例摘要
     */
    private List<IterationRunVO> runs = new ArrayList<>();

    /**
     * 链上一轮产出实例的摘要
     */
    @Data
    public static class IterationRunVO {

        /**
         * 实例 id
         */
        private String instanceId;

        /**
         * 实例状态
         */
        private String status;

        /**
         * 实例创建时间
         */
        private LocalDateTime createTime;
    }
}
