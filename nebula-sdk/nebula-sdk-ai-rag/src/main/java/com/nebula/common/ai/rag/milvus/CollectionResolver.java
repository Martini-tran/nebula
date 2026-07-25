package com.nebula.common.ai.rag.milvus;

import com.nebula.common.ai.properties.AiProperties;

/**
 * collection 逻辑名 → Milvus 实际操作名解析器。
 *
 * <p>上层四场景只认逻辑名（{@code nebula_kb} 等）。本解析器统一把逻辑名映射为 Milvus 侧可操作的名字：
 * <b>逻辑名本身即作为 alias 使用</b>——{@link CollectionInitializer} 建物理 collection（含维度/版本）后为其创建
 * 一个名为逻辑名的 alias，Milvus 的 upsert/search/delete 对 alias 透明。故本实现直接返回逻辑名即可，模型迁移时
 * 只需把 alias 原子指向新物理 collection，上层无感（见 docs 第六章）。
 *
 * <p>若未启用 {@code auto-create-collection}（用户手工建库且未建 alias），返回物理名作兜底。
 *
 * @author nebula
 */
public class CollectionResolver {

    private final AiProperties.Milvus config;

    private final int dimension;

    public CollectionResolver(AiProperties.Milvus config, int dimension) {
        this.config = config;
        this.dimension = dimension;
    }

    /**
     * 解析逻辑名为 Milvus 侧操作名。
     *
     * @param logical 逻辑名（如 {@code nebula_kb}）
     * @return alias（逻辑名）或物理名
     */
    public String resolve(String logical) {
        if (config.isAutoCreateCollection()) {
            // 初始化器已建 alias=逻辑名 → 直接用逻辑名，迁移无感
            return logical;
        }
        return physicalName(logical);
    }

    /**
     * 逻辑名对应的物理 collection 名（含维度/版本）。
     *
     * @param logical 逻辑名
     * @return 物理名
     */
    public String physicalName(String logical) {
        return MilvusCollections.physicalName(logical, config.getCollectionPrefix(), dimension);
    }
}
