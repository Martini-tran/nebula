package com.nebula.common.ai.rag.flowexample;

import com.nebula.common.ai.rag.EmbeddingProvider;
import com.nebula.common.ai.rag.VectorMatch;
import com.nebula.common.ai.rag.VectorQuery;
import com.nebula.common.ai.rag.VectorRecord;
import com.nebula.common.ai.rag.VectorStore;
import com.nebula.common.ai.rag.milvus.MilvusCollections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flow few-shot 示例服务（场景②）。
 *
 * <p>把已有流程的「名称 + 描述」向量化进 {@code nebula_flow_example}，供 Flow Copilot 生成新流程前语义召回相似历史
 * 流程作 few-shot 示例注入 system prompt——让模型「照着相近的既有流程写」，比零样本生成更贴合本系统的节点/编排实践。
 *
 * <p>纯向量 CRUD，不依赖任何业务 Mapper：索引源 {@code ai_flow} 由能读它的宿主（manager 的 {@code FlowExampleIndexer}）
 * 读取后调 {@link #upsert} 传入；召回接线也在 manager 的 {@code FlowCopilotService}（「生成前」这个时机 sdk 不认识，
 * sdk 只提供 {@link #recall} 能力）。
 *
 * <p><b>collection {@code nebula_flow_example} 约定</b>（见批次1 CollectionInitializer）：partitionKey={@code owner}，
 * 标量字段 {@code flow_code}/{@code node_types}。{@code ai_flow} 表无 owner 列，示例全局共享，故 owner 统一取
 * {@link #OWNER_GLOBAL}（partitionKey 不允许 null，用固定值占位）。
 *
 * <p><b>可用性铁律</b>：与批次1/2 一致，向量写入/检索故障仅 {@code log.warn} 降级（few-shot 是增强层，故障时 Copilot
 * 退化为零样本生成，仍可用），绝不外抛。
 *
 * @author nebula
 */
@Slf4j
public class FlowExampleService {

    /**
     * 全局共享 owner 分区值。{@code ai_flow} 无 owner 概念，示例不按租户隔离；partitionKey 不允许 null，故用固定占位。
     */
    public static final String OWNER_GLOBAL = "global";

    /**
     * 默认召回条数
     */
    private static final int DEFAULT_TOP_K = 3;

    private final VectorStore vectorStore;
    private final EmbeddingProvider embeddingProvider;

    public FlowExampleService(VectorStore vectorStore, EmbeddingProvider embeddingProvider) {
        this.vectorStore = vectorStore;
        this.embeddingProvider = embeddingProvider;
    }

    /**
     * 索引一个流程：embed「名称 + 描述」→ upsert 进 {@code nebula_flow_example}（pk=flowCode）。
     * 幂等：同 flowCode 重复索引覆盖旧向量。向量写失败仅告警降级。
     *
     * @param flowCode    流程编码（作 pk）
     * @param name        流程名称
     * @param description 流程描述
     * @param nodeTypes   节点类型摘要（逗号分隔，仅标量回显，暂不参与过滤）
     */
    public void upsert(String flowCode, String name, String description, String nodeTypes) {
        if (!StringUtils.hasText(flowCode)) {
            return;
        }
        try {
            List<float[]> vectors = embeddingProvider.embed(List.of(embedText(name, description)));
            if (vectors.isEmpty()) {
                return;
            }
            VectorRecord vr = buildRecord(flowCode, name, description, nodeTypes, vectors.get(0));
            vectorStore.upsert(MilvusCollections.FLOW_EXAMPLE, List.of(vr));
        } catch (RuntimeException e) {
            log.warn("流程示例向量索引失败（few-shot 降级）：flowCode={}, {}", flowCode, e.getMessage());
        }
    }

    /**
     * 批量索引流程：一次 embed 全部文本 + 一次批量 upsert（与 {@code ToolCatalogService.index} 同构），
     * 而非逐条走 {@link #upsert}——后者每条一次 embed HTTP + 一次 Milvus 写，N 条即 N 次网络往返；批量后仅
     * {@code ceil(N/batchSize)} 次 embed + 1 次写。向量故障仅告警降级。
     *
     * @param examples 待索引流程摘要
     * @return 实际写入的条数
     */
    public int indexAll(List<FlowExampleSource> examples) {
        if (examples == null || examples.isEmpty()) {
            return 0;
        }
        List<FlowExampleSource> valid = new ArrayList<>(examples.size());
        List<String> texts = new ArrayList<>(examples.size());
        for (FlowExampleSource e : examples) {
            if (e == null || !StringUtils.hasText(e.flowCode())) {
                continue;
            }
            valid.add(e);
            texts.add(embedText(e.name(), e.description()));
        }
        if (valid.isEmpty()) {
            return 0;
        }
        try {
            List<float[]> vectors = embeddingProvider.embed(texts);
            List<VectorRecord> records = new ArrayList<>(valid.size());
            for (int i = 0; i < valid.size() && i < vectors.size(); i++) {
                FlowExampleSource e = valid.get(i);
                records.add(buildRecord(e.flowCode(), e.name(), e.description(), e.nodeTypes(), vectors.get(i)));
            }
            vectorStore.upsert(MilvusCollections.FLOW_EXAMPLE, records);
            return records.size();
        } catch (RuntimeException e) {
            log.warn("流程示例批量索引失败（few-shot 降级）：{}", e.getMessage());
            return 0;
        }
    }

    /**
     * 组装一条流程示例向量记录：pk=flowCode，scalars 对齐 nebula_flow_example（owner/flow_code/node_types），
     * name 存 metadata 供召回回显，content=描述副本。单条 upsert 与批量 indexAll 共用，保证结构一致。
     */
    private VectorRecord buildRecord(String flowCode, String name, String description,
                                     String nodeTypes, float[] vector) {
        Map<String, Object> scalars = new LinkedHashMap<>();
        scalars.put("owner", OWNER_GLOBAL);
        scalars.put("flow_code", flowCode);
        scalars.put("node_types", nodeTypes);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        return new VectorRecord(flowCode, vector, description, scalars, metadata);
    }

    /**
     * 语义召回相似流程：embed(query) → 在全局分区内 top-k 相似度检索，返回流程摘要（降序）。
     *
     * @param query    查询文本（用户对新流程的意图描述）
     * @param topK     返回条数（{@code <=0} 取默认）
     * @param minScore 最小归一化得分阈值（{@code <=0} 不过滤）
     * @return 命中流程示例（降序，可能为空）
     */
    public List<FlowExample> recall(String query, int topK, double minScore) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        int limit = topK > 0 ? topK : DEFAULT_TOP_K;
        try {
            List<float[]> vectors = embeddingProvider.embed(List.of(query));
            if (vectors.isEmpty()) {
                return List.of();
            }
            VectorQuery vq = new VectorQuery(
                    MilvusCollections.FLOW_EXAMPLE,
                    OWNER_GLOBAL,
                    vectors.get(0),
                    limit,
                    null,
                    minScore);
            List<VectorMatch> matches = vectorStore.search(vq);
            List<FlowExample> examples = new ArrayList<>(matches.size());
            for (VectorMatch match : matches) {
                Map<String, Object> scalars = match.scalars();
                String flowCode = scalar(scalars, "flow_code", match.pk());
                String name = match.metadata() == null ? null : str(match.metadata().get("name"));
                examples.add(new FlowExample(flowCode, name, match.content(), match.score()));
            }
            return examples;
        } catch (RuntimeException e) {
            log.warn("流程示例语义召回失败（few-shot 降级为零样本生成）：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 流程的可 embed 文本：名称 + 描述（描述缺省时退化为名称）。
     */
    private String embedText(String name, String description) {
        String safeName = StringUtils.hasText(name) ? name : "";
        if (StringUtils.hasText(description)) {
            return StringUtils.hasText(safeName) ? safeName + "：" + description : description;
        }
        return safeName;
    }

    private static String scalar(Map<String, Object> scalars, String key, String def) {
        Object v = scalars == null ? null : scalars.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 索引源单元：宿主（manager）从 {@code ai_flow} 读取后传入。放在 SDK 侧使 {@link #indexAll} 契约自包含，
     * 宿主无需感知向量库结构。
     *
     * @param flowCode    流程编码
     * @param name        流程名称
     * @param description 流程描述
     * @param nodeTypes   节点类型摘要（逗号分隔，可空）
     */
    public record FlowExampleSource(String flowCode, String name, String description, String nodeTypes) {
    }
}
