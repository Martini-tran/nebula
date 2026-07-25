package com.nebula.common.ai.rag.toolcatalog;

import com.nebula.common.ai.flow.ToolDefinition;
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
 * 工具目录语义检索服务（场景④）。
 *
 * <p>把「工具全集」向量化进 {@code nebula_tool_catalog}，让 Copilot / Agent 能「语义找工具」（{@code search_tools}）
 * 而非只 {@code list_tools} 全量列举——工具数量增长后，全量注入 system prompt 既费 token 又稀释注意力，语义检索
 * 只召回与当前意图相关的 top-k 工具。
 *
 * <p>纯向量 CRUD，不依赖任何业务 Mapper：索引源是内存里的 {@link ToolDefinition} 全集（由 {@code ToolCatalogIndexer}
 * 遍历 {@code ToolRegistry.all()} 传入），与真相源（代码）一致，无需读 {@code ai_tool} 表。
 *
 * <p><b>collection {@code nebula_tool_catalog} 约定</b>（见批次1 CollectionInitializer）：partitionKey={@code item_type}
 * （工具固定取值 {@link #ITEM_TYPE_TOOL}），标量字段 {@code code}。故 {@link #index} 的 {@link VectorRecord#pk()} 用
 * 工具 code、{@code scalars} 填 {@code item_type}/{@code code}；{@link #search} 的 {@code item_type} 走
 * {@link VectorQuery#partition()} 分区裁剪，命中返回工具 code 列表由调用方回填元数据。
 *
 * <p><b>可用性铁律</b>：与批次1/2 一致，向量写入/检索故障仅 {@code log.warn} 降级（工具目录是加速层，故障时
 * 回退全量 {@code list_tools} 即可），绝不外抛进 Copilot / Agent 主循环。
 *
 * @author nebula
 */
@Slf4j
public class ToolCatalogService {

    /**
     * 目录条目类型：工具（partitionKey 取值）。为节点/其他可检索项预留区分维度。
     */
    public static final String ITEM_TYPE_TOOL = "tool";

    /**
     * 默认召回条数
     */
    private static final int DEFAULT_TOP_K = 5;

    private final VectorStore vectorStore;
    private final EmbeddingProvider embeddingProvider;

    public ToolCatalogService(VectorStore vectorStore, EmbeddingProvider embeddingProvider) {
        this.vectorStore = vectorStore;
        this.embeddingProvider = embeddingProvider;
    }

    /**
     * 索引一批工具：embed 每个工具的「名称 + 描述」→ upsert 进 {@code nebula_tool_catalog}（pk=工具 code）。
     * 幂等：同 code 重复索引覆盖旧向量。向量写失败仅告警降级（不影响运行，仅该工具暂不可语义检索）。
     *
     * @param tools 待索引工具（通常来自 {@code ToolRegistry.all()}）
     * @return 成功索引的工具数
     */
    public int index(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return 0;
        }
        List<ToolDefinition> valid = new ArrayList<>(tools.size());
        List<String> texts = new ArrayList<>(tools.size());
        for (ToolDefinition tool : tools) {
            if (tool == null || !StringUtils.hasText(tool.code())) {
                continue;
            }
            valid.add(tool);
            texts.add(embedText(tool));
        }
        if (valid.isEmpty()) {
            return 0;
        }
        try {
            List<float[]> vectors = embeddingProvider.embed(texts);
            List<VectorRecord> records = new ArrayList<>(valid.size());
            for (int i = 0; i < valid.size() && i < vectors.size(); i++) {
                ToolDefinition tool = valid.get(i);
                Map<String, Object> scalars = new LinkedHashMap<>();
                scalars.put("item_type", ITEM_TYPE_TOOL);
                scalars.put("code", tool.code());
                // content 复用已算好的 texts.get(i)，避免二次 embedText 及「两处文本须一致」的隐性耦合
                records.add(new VectorRecord(tool.code(), vectors.get(i), texts.get(i), scalars, null));
            }
            vectorStore.upsert(MilvusCollections.TOOL_CATALOG, records);
            return records.size();
        } catch (RuntimeException e) {
            log.warn("工具目录向量索引失败（语义检索降级，回退全量 list_tools）：{}", e.getMessage());
            return 0;
        }
    }

    /**
     * 语义检索工具：embed(query) → 在工具分区内 top-k 相似度检索，返回命中的工具 code（降序）。
     *
     * @param query 查询文本（用户意图 / 任务描述）
     * @param topK  返回条数（{@code <=0} 取默认）
     * @return 命中工具 code 列表（降序，可能为空）
     */
    public List<String> search(String query, int topK) {
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
                    MilvusCollections.TOOL_CATALOG,
                    ITEM_TYPE_TOOL,
                    vectors.get(0),
                    limit,
                    null,
                    0);
            List<VectorMatch> matches = vectorStore.search(vq);
            List<String> codes = new ArrayList<>(matches.size());
            for (VectorMatch match : matches) {
                Object code = match.scalars() == null ? null : match.scalars().get("code");
                codes.add(code != null ? String.valueOf(code) : match.pk());
            }
            return codes;
        } catch (RuntimeException e) {
            log.warn("工具语义检索失败（降级返回空，调用方回退全量 list_tools）：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 工具的可 embed 文本：名称 + 描述（描述缺省时退化为名称/编码），用于向量化。
     */
    private String embedText(ToolDefinition tool) {
        String name = StringUtils.hasText(tool.name()) ? tool.name() : tool.code();
        String description = tool.description();
        if (StringUtils.hasText(description)) {
            return name + "：" + description;
        }
        return name;
    }
}
