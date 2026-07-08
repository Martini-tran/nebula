package com.nebula.blog.dto.admin;

import lombok.Data;

import java.util.Map;

/**
 * 迭代链回调落库请求（编排 webhook 消费端）
 * 对齐通用回调 body 结构（见 docs/编排回调Webhook设计.md 第七章）：顶层是 chainId/seq/agentCode/instanceId，
 * 产物在 {@code context} 里（Agent 用 outputKey 写进 context 的键，如 articleTitle/articleBody/outline）。
 * manager 的 IterationDriver 每轮 advance 成功后 POST 本结构。幂等键 = {@code (chainId, seq)}。
 *
 * @author nebula
 */
@Data
public class SeriesIterationAppendRequest {

    /**
     * 投递唯一标识（可空，仅日志）
     */
    private String deliveryId;

    /**
     * 事件名（ITERATION_ADVANCED）
     */
    private String event;

    /**
     * 迭代链 chainId（chainId→seriesId 映射键；首轮据此建系列并记 chain_id）
     */
    private String chainId;

    /**
     * 第几轮（= 第几篇，从 1 起）。与 chainId 组成幂等键
     */
    private Integer seq;

    /**
     * Agent 编码
     */
    private String agentCode;

    /**
     * 本轮实例 id
     */
    private String instanceId;

    /**
     * 产物上下文：Agent 用 outputKey 写进 context 的键。约定取
     * {@code articleTitle}/{@code articleBody}/{@code articleSummary}/{@code seriesName}/{@code seriesSlug}。
     */
    private Map<String, Object> context;

    /* ---- 从 context 安全取约定键（blog 消费端便捷读取） ---- */

    /** 本篇标题 */
    public String articleTitle() {
        return str("articleTitle");
    }

    /** 本篇正文（Markdown） */
    public String articleBody() {
        return str("articleBody");
    }

    /** 本篇摘要 */
    public String articleSummary() {
        return str("articleSummary");
    }

    /** 系列名称（首轮建系列用；缺省用 chainId） */
    public String seriesName() {
        return str("seriesName");
    }

    /** 系列 slug（首轮建系列用；空则据 chainId 生成） */
    public String seriesSlug() {
        return str("seriesSlug");
    }

    private String str(String key) {
        if (context == null) {
            return null;
        }
        Object v = context.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
