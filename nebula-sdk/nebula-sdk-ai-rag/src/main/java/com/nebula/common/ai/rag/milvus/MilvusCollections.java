package com.nebula.common.ai.rag.milvus;

import java.util.List;

/**
 * Milvus collection 逻辑名与公共字段常量。
 *
 * <p>四场景各一个 collection（共享同一 embedding 维度），逻辑名对上层稳定；物理名由 {@link #physicalName} 拼
 * {@code {prefix}{logical}__d{dim}__v1} 并嵌入维度与版本，换 embedding 模型时经 Milvus alias 原子切换（见 docs 第六章）。
 *
 * <p>公共字段（4 个 collection 都有）：{@code pk}（主键，业务确定性键）、{@code vector}（向量列）、{@code scene}
 * （冗余场景标识）、{@code content}（正文副本）、{@code create_ts}（写入毫秒时间戳）。各 collection 专有标量另见其
 * partitionKey 与标量字段定义。
 *
 * @author nebula
 */
public final class MilvusCollections {

    /**
     * 场景① 知识库 RAG collection 逻辑名
     */
    public static final String KB = "nebula_kb";

    /**
     * 场景② Flow Copilot few-shot collection 逻辑名
     */
    public static final String FLOW_EXAMPLE = "nebula_flow_example";

    /**
     * 场景③ 长期记忆 collection 逻辑名
     */
    public static final String MEMORY = "nebula_memory";

    /**
     * 场景④ 节点/工具目录 collection 逻辑名
     */
    public static final String TOOL_CATALOG = "nebula_tool_catalog";

    /**
     * 主键字段（业务确定性键，upsert 幂等靠它）
     */
    public static final String FIELD_PK = "pk";

    /**
     * 向量字段
     */
    public static final String FIELD_VECTOR = "vector";

    /**
     * 场景冗余标识字段
     */
    public static final String FIELD_SCENE = "scene";

    /**
     * 正文副本字段
     */
    public static final String FIELD_CONTENT = "content";

    /**
     * 写入时间戳字段（毫秒）
     */
    public static final String FIELD_CREATE_TS = "create_ts";

    /**
     * 正文字段最大长度（VarChar）
     */
    public static final int CONTENT_MAX_LENGTH = 8192;

    /**
     * 主键字段最大长度（VarChar）
     */
    public static final int PK_MAX_LENGTH = 512;

    /**
     * 通用标量字段最大长度（VarChar）
     */
    public static final int SCALAR_MAX_LENGTH = 256;

    /**
     * 物理 collection schema 版本位（批次4）。物理名嵌入维度与本版本以支撑 alias 迁移：换 embedding 模型/维度或重建
     * 索引时，建 {@code __v{n+1}} 新库 → 全量 backfill → alias 原子切换 → 删旧（见 {@code docs/向量检索迁移runbook.md}）。
     * 此处集中一处便于迁移时递增，避免版本号散落在字符串拼接里。
     */
    public static final int SCHEMA_VERSION = 1;

    /**
     * 全部四个 collection 逻辑名
     */
    public static final List<String> ALL = List.of(KB, FLOW_EXAMPLE, MEMORY, TOOL_CATALOG);

    private MilvusCollections() {
    }

    /**
     * 逻辑名 → 物理名：{@code {prefix}{logical}__d{dim}__v{SCHEMA_VERSION}}，嵌入维度与版本以支撑 alias 迁移。
     * 逻辑名已含约定前缀 {@code nebula_}，故仅在 prefix 非默认时额外拼接（避免重复前缀）。
     *
     * @param logical    逻辑名（如 {@code nebula_kb}）
     * @param prefix     配置的 collection 前缀（默认 {@code nebula_}）
     * @param dimension  向量维度
     * @return 物理 collection 名
     */
    public static String physicalName(String logical, String prefix, int dimension) {
        String base = logical;
        if (prefix != null && !prefix.isEmpty() && !"nebula_".equals(prefix) && !logical.startsWith(prefix)) {
            base = prefix + logical;
        }
        return base + "__d" + dimension + "__v" + SCHEMA_VERSION;
    }
}
