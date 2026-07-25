package com.nebula.common.ai.rag.milvus;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.VectorChunk;
import com.nebula.common.ai.rag.VectorMatch;
import com.nebula.common.ai.rag.VectorQuery;
import com.nebula.common.ai.rag.VectorRecord;
import com.nebula.common.ai.rag.VectorStore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus 版 {@link VectorStore} 实现（{@code MilvusClientV2}）。
 *
 * <p>知识库门面（{@link #upsert(List, List)} / {@link #search(String, float[], int)} / {@link #deleteByDoc}）内部委托到
 * 泛化方法（collection={@code nebula_kb}，partition=kbCode）。泛化方法把 {@link VectorRecord} 转 Gson {@link JsonObject}
 * upsert、把 {@link VectorQuery} 转 {@code SearchReq} 检索并归一化得分。
 *
 * <p><b>得分归一化</b>：Milvus COSINE 返回相似度（越大越近），本实现统一转 {@code (cos+1)/2 → [0,1]} 回填。
 * 逻辑名经 {@link CollectionResolver} 解析物理名（alias 优先）。
 *
 * @author nebula
 */
@Slf4j
public class MilvusVectorStore implements VectorStore {

    private final MilvusClientV2 client;
    private final AiProperties.Milvus config;
    private final CollectionResolver resolver;
    private final Gson gson = new Gson();

    public MilvusVectorStore(MilvusClientV2 client, AiProperties.Milvus config, CollectionResolver resolver) {
        this.client = client;
        this.config = config;
        this.resolver = resolver;
    }

    // —— 知识库门面（委托泛化方法）——

    @Override
    public void upsert(List<VectorChunk> chunks, List<float[]> embeddings) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalArgumentException("切片与向量数量不一致");
        }
        List<VectorRecord> records = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            VectorChunk c = chunks.get(i);
            Map<String, Object> scalars = new LinkedHashMap<>();
            scalars.put("kb_code", c.kbCode());
            scalars.put("doc_id", c.docId());
            scalars.put("chunk_index", c.chunkIndex());
            String pk = c.kbCode() + ":" + c.docId() + ":" + c.chunkIndex();
            records.add(new VectorRecord(pk, embeddings.get(i), c.content(), scalars, c.metadata()));
        }
        upsert(MilvusCollections.KB, records);
    }

    @Override
    public List<VectorChunk> search(String kbCode, float[] queryVector, int topK) {
        VectorQuery query = new VectorQuery(MilvusCollections.KB, kbCode, queryVector, topK, null, 0);
        List<VectorMatch> matches = search(query);
        List<VectorChunk> chunks = new ArrayList<>(matches.size());
        for (VectorMatch m : matches) {
            Map<String, Object> scalars = m.scalars();
            String docId = scalars == null ? null : str(scalars.get("doc_id"));
            int chunkIndex = scalars == null ? 0 : intOf(scalars.get("chunk_index"));
            chunks.add(new VectorChunk(kbCode, docId, chunkIndex, m.content(), m.score(), m.metadata()));
        }
        return chunks;
    }

    @Override
    public void deleteByDoc(String kbCode, String docId) {
        String filter = "kb_code == \"" + escape(kbCode) + "\" && doc_id == \"" + escape(docId) + "\"";
        delete(MilvusCollections.KB, filter);
    }

    // —— 泛化方法 ——

    @Override
    public void upsert(String collection, List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String physical = resolver.resolve(collection);
        long now = System.currentTimeMillis();
        List<JsonObject> rows = new ArrayList<>(records.size());
        for (VectorRecord r : records) {
            JsonObject row = new JsonObject();
            row.addProperty(MilvusCollections.FIELD_PK, r.pk());
            row.add(MilvusCollections.FIELD_VECTOR, gson.toJsonTree(toFloatList(r.vector())));
            row.addProperty(MilvusCollections.FIELD_SCENE, collection);
            row.addProperty(MilvusCollections.FIELD_CONTENT, clip(r.content()));
            row.addProperty(MilvusCollections.FIELD_CREATE_TS, now);
            if (r.scalars() != null) {
                r.scalars().forEach((k, v) -> addScalar(row, k, v));
            }
            rows.add(row);
        }
        client.upsert(UpsertReq.builder()
                .collectionName(physical)
                .data(rows)
                .build());
    }

    @Override
    public List<VectorMatch> search(VectorQuery query) {
        String physical = resolver.resolve(query.collection());
        List<BaseVector> vectors = List.of(new FloatVec(toFloatList(query.vector())));

        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(physical)
                .data(vectors)
                .annsField(MilvusCollections.FIELD_VECTOR)
                .topK(query.topK())
                .outputFields(List.of(
                        MilvusCollections.FIELD_PK,
                        MilvusCollections.FIELD_CONTENT));
        String filter = composeFilter(query);
        if (filter != null && !filter.isEmpty()) {
            builder.filter(filter);
        }
        Map<String, Object> searchParams = new LinkedHashMap<>();
        searchParams.put("ef", config.getHnswEf());
        builder.searchParams(searchParams);

        SearchResp resp = client.search(builder.build());
        List<VectorMatch> result = new ArrayList<>();
        List<List<SearchResp.SearchResult>> all = resp.getSearchResults();
        if (all == null || all.isEmpty()) {
            return result;
        }
        for (SearchResp.SearchResult hit : all.get(0)) {
            double normalized = normalizeScore(hit.getScore());
            if (query.minScore() > 0 && normalized < query.minScore()) {
                continue;
            }
            Map<String, Object> entity = hit.getEntity();
            String pk = entity == null ? str(hit.getId()) : str(entity.get(MilvusCollections.FIELD_PK));
            String content = entity == null ? null : str(entity.get(MilvusCollections.FIELD_CONTENT));
            result.add(new VectorMatch(pk, content, normalized, entity, null));
        }
        return result;
    }

    @Override
    public void delete(String collection, String filterExpr) {
        if (filterExpr == null || filterExpr.isEmpty()) {
            return;
        }
        String physical = resolver.resolve(collection);
        client.delete(DeleteReq.builder()
                .collectionName(physical)
                .filter(filterExpr)
                .build());
    }

    /**
     * 合并 partition 过滤（partitionKey 走标量 == 过滤）与显式 filterExpr。
     * 约定各 collection 的 partitionKey 标量列名与 partition 值一一映射；此处以通用列名映射兼容四场景。
     */
    private String composeFilter(VectorQuery query) {
        List<String> parts = new ArrayList<>();
        if (query.partition() != null && !query.partition().isEmpty()) {
            String partitionField = partitionFieldOf(query.collection());
            if (partitionField != null) {
                parts.add(partitionField + " == \"" + escape(query.partition()) + "\"");
            }
        }
        if (query.filterExpr() != null && !query.filterExpr().isEmpty()) {
            parts.add("(" + query.filterExpr() + ")");
        }
        return String.join(" && ", parts);
    }

    /**
     * 各 collection 的 partitionKey 标量列名（用于 partition 值过滤）。
     */
    private String partitionFieldOf(String collection) {
        return switch (collection) {
            case MilvusCollections.KB -> "kb_code";
            case MilvusCollections.FLOW_EXAMPLE -> "owner";
            case MilvusCollections.MEMORY -> "agent_code";
            case MilvusCollections.TOOL_CATALOG -> "item_type";
            default -> null;
        };
    }

    /**
     * COSINE 相似度 {@code [-1,1]} 归一化到 {@code [0,1]}；null 视为 0。
     */
    private static double normalizeScore(Float score) {
        if (score == null) {
            return 0;
        }
        double v = (score + 1.0) / 2.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    private void addScalar(JsonObject row, String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        if (value instanceof Number n) {
            row.addProperty(key, n);
        } else if (value instanceof Boolean b) {
            row.addProperty(key, b);
        } else {
            row.addProperty(key, String.valueOf(value));
        }
    }

    private static List<Float> toFloatList(float[] vector) {
        if (vector == null) {
            return List.of();
        }
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private static String clip(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= MilvusCollections.CONTENT_MAX_LENGTH
                ? content : content.substring(0, MilvusCollections.CONTENT_MAX_LENGTH);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int intOf(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o == null ? 0 : Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
