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
     * 产物上下文：Agent 用 outputKey 写进 context 的键。实际结构（见迭代回调 body）：
     * <pre>
     * context = {
     *   article       : { title, content, seq, prevSeq, nextSeq },  // 本轮产物，嵌套 Map
     *   seriesName     : "...",                                       // 顶层字符串
     *   updatedOutline : { title, parts[] },                         // 全量大纲
     *   accumulated    : { usedExamples[], coveredTopics[], style }, // 累积上下文
     *   topic, isComplete, seriesState ...
     * }
     * </pre>
     * 故本篇标题/正文要从 {@code context.article.*} 里取，而非顶层键。
     */
    private Map<String, Object> context;

    /* ---- 从 context 安全取约定键（blog 消费端便捷读取） ---- */

    /** 本篇标题（context.article.title） */
    public String articleTitle() {
        return articleField("title");
    }

    /** 本篇正文（Markdown，context.article.content） */
    public String articleBody() {
        return articleField("content");
    }

    /** 本篇摘要（context.article.summary，当前产物无此字段则为空） */
    public String articleSummary() {
        return articleField("summary");
    }

    /** 系列名称（首轮建系列用；缺省用 chainId） */
    public String seriesName() {
        return str("seriesName");
    }

    /** 系列 slug（首轮建系列用；空则据 chainId 生成） */
    public String seriesSlug() {
        return str("seriesSlug");
    }

    /** 从 context.article 子 Map 取字符串字段 */
    @SuppressWarnings("unchecked")
    private String articleField(String key) {
        if (context == null) {
            return null;
        }
        Object article = context.get("article");
        if (!(article instanceof Map)) {
            return null;
        }
        Object v = ((Map<String, Object>) article).get(key);
        return v == null ? null : String.valueOf(v);
    }

    private String str(String key) {
        if (context == null) {
            return null;
        }
        Object v = context.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
