package com.nebula.common.ai.rag.milvus;

import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.EmbeddingProvider;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.utility.request.AlterAliasReq;
import io.milvus.v2.service.utility.request.CreateAliasReq;
import io.milvus.v2.service.utility.request.ListAliasesReq;
import io.milvus.v2.service.utility.response.ListAliasResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus collection 初始化器（{@link ApplicationRunner}）。
 *
 * <p>启动时按 {@code auto-create-collection} 幂等建库：为四场景各建一个物理 collection（含公共字段 + 场景专有标量 +
 * partitionKey），建 HNSW/COSINE 索引，load，并为其创建名为逻辑名的 alias（模型迁移经 alias 原子切换，见 docs 第六章）。
 * 已存在则跳过建库、仅确保 alias 指向当前物理名。任何单场景初始化失败只告警不阻断启动（可用性铁律）。
 *
 * @author nebula
 */
@Slf4j
public class CollectionInitializer implements ApplicationRunner {

    private final MilvusClientV2 client;
    private final AiProperties.Milvus config;
    private final CollectionResolver resolver;
    private final int dimension;

    public CollectionInitializer(MilvusClientV2 client,
                                 AiProperties.Milvus config,
                                 CollectionResolver resolver,
                                 EmbeddingProvider embeddingProvider) {
        this.client = client;
        this.config = config;
        this.resolver = resolver;
        this.dimension = embeddingProvider.dimension();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!config.isAutoCreateCollection()) {
            log.info("Milvus auto-create-collection=false，跳过 collection 自动初始化");
            return;
        }
        for (String logical : MilvusCollections.ALL) {
            try {
                ensureCollection(logical);
            } catch (Exception e) {
                log.warn("初始化 Milvus collection {} 失败（不阻断启动）：{}", logical, e.getMessage());
            }
        }
    }

    /**
     * 幂等确保单个逻辑 collection 就绪：建物理库+索引+load → 建/校正 alias。
     */
    private void ensureCollection(String logical) {
        String physical = resolver.physicalName(logical);
        boolean exists = Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder()
                .collectionName(physical)
                .build()));
        if (!exists) {
            createCollection(logical, physical);
            client.loadCollection(LoadCollectionReq.builder().collectionName(physical).build());
            log.info("已创建并加载 Milvus collection {}（物理名 {}，dim={}）", logical, physical, dimension);
        }
        ensureAlias(logical, physical);
    }

    /**
     * 建物理 collection：公共字段 + 场景专有标量 + partitionKey + HNSW/COSINE 索引。
     */
    private void createCollection(String logical, String physical) {
        CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName(MilvusCollections.FIELD_PK)
                .dataType(DataType.VarChar)
                .maxLength(MilvusCollections.PK_MAX_LENGTH)
                .isPrimaryKey(true)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(MilvusCollections.FIELD_VECTOR)
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(MilvusCollections.FIELD_SCENE)
                .dataType(DataType.VarChar)
                .maxLength(MilvusCollections.SCALAR_MAX_LENGTH)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(MilvusCollections.FIELD_CONTENT)
                .dataType(DataType.VarChar)
                .maxLength(MilvusCollections.CONTENT_MAX_LENGTH)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(MilvusCollections.FIELD_CREATE_TS)
                .dataType(DataType.Int64)
                .build());
        for (SceneField field : sceneFields(logical)) {
            schema.addField(AddFieldReq.builder()
                    .fieldName(field.name())
                    .dataType(field.dataType())
                    .maxLength(field.dataType() == DataType.VarChar ? MilvusCollections.SCALAR_MAX_LENGTH : null)
                    .isPartitionKey(field.partitionKey())
                    .build());
        }

        IndexParam indexParam = IndexParam.builder()
                .fieldName(MilvusCollections.FIELD_VECTOR)
                .indexType(resolveIndexType())
                .metricType(resolveMetricType())
                .extraParams(hnswExtraParams())
                .build();

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(physical)
                .collectionSchema(schema)
                .indexParams(List.of(indexParam))
                .build());
    }

    /**
     * 确保 alias=逻辑名 存在且指向当前物理名。已指向则跳过；指向他处则 alter；不存在则 create。
     */
    private void ensureAlias(String logical, String physical) {
        try {
            ListAliasResp aliases = client.listAliases(ListAliasesReq.builder()
                    .collectionName(physical)
                    .build());
            if (aliases != null && aliases.getAlias() != null && aliases.getAlias().contains(logical)) {
                return;
            }
            client.createAlias(CreateAliasReq.builder()
                    .collectionName(physical)
                    .alias(logical)
                    .build());
            log.info("已为 collection {} 创建 alias {}", physical, logical);
        } catch (Exception e) {
            // alias 已被占用（指向旧物理名）→ 改指向；其余异常告警
            try {
                client.alterAlias(AlterAliasReq.builder()
                        .collectionName(physical)
                        .alias(logical)
                        .build());
                log.info("已把 alias {} 改指向 {}", logical, physical);
            } catch (Exception ex) {
                log.warn("处理 alias {} → {} 失败：{}", logical, physical, ex.getMessage());
            }
        }
    }

    /**
     * 各场景专有标量字段 + partitionKey（见 docs 第六章 6.2）。
     */
    private List<SceneField> sceneFields(String logical) {
        List<SceneField> fields = new ArrayList<>();
        switch (logical) {
            case MilvusCollections.KB -> {
                fields.add(new SceneField("kb_code", DataType.VarChar, true));
                fields.add(new SceneField("doc_id", DataType.VarChar, false));
                fields.add(new SceneField("chunk_index", DataType.Int64, false));
            }
            case MilvusCollections.FLOW_EXAMPLE -> {
                fields.add(new SceneField("owner", DataType.VarChar, true));
                fields.add(new SceneField("flow_code", DataType.VarChar, false));
                fields.add(new SceneField("node_types", DataType.VarChar, false));
            }
            case MilvusCollections.MEMORY -> {
                fields.add(new SceneField("agent_code", DataType.VarChar, true));
                fields.add(new SceneField("user_id", DataType.VarChar, false));
                fields.add(new SceneField("conversation_id", DataType.VarChar, false));
                fields.add(new SceneField("mem_type", DataType.VarChar, false));
            }
            case MilvusCollections.TOOL_CATALOG -> {
                fields.add(new SceneField("item_type", DataType.VarChar, true));
                fields.add(new SceneField("code", DataType.VarChar, false));
            }
            default -> {
            }
        }
        return fields;
    }

    private IndexParam.IndexType resolveIndexType() {
        try {
            return IndexParam.IndexType.valueOf(config.getIndex());
        } catch (Exception e) {
            return IndexParam.IndexType.HNSW;
        }
    }

    private IndexParam.MetricType resolveMetricType() {
        try {
            return IndexParam.MetricType.valueOf(config.getMetric());
        } catch (Exception e) {
            return IndexParam.MetricType.COSINE;
        }
    }

    private Map<String, Object> hnswExtraParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("M", config.getHnswM());
        params.put("efConstruction", config.getHnswEfConstruction());
        return params;
    }

    /**
     * 场景标量字段描述。
     *
     * @param name         列名
     * @param dataType     类型
     * @param partitionKey 是否 partitionKey（每 collection 恰一个）
     */
    private record SceneField(String name, DataType dataType, boolean partitionKey) {
    }
}
