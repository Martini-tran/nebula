package com.nebula.common.ai.api;

/**
 * 向量重索引标记 SPI（批次4 need_reindex 对账）。
 *
 * <p>解决「双写不一致靠对账兜底」这条设计承诺：向量语义召回版记忆（{@code VectorLongTermMemory}）双写 DB + Milvus 时，
 * 若向量写入/删除失败，仅 DB 落成而向量缺失——该条记忆将永远召不回（或删除后残留成幽灵命中），且无信号、无修复入口。
 * 本接口让装饰器在向量侧失败时把记忆行标记为「待重索引」，由对账任务（{@code MemoryReindexReconciler}）扫描后补写向量。
 *
 * <p><b>为何是独立窄接口而非并进 {@link LongTermMemory}</b>：置位是 DB 真相源侧的能力（改 {@code need_reindex} 列），
 * 而装饰器不应反向依赖具体 Mapper 实现。真相源实现（{@code DatabaseLongTermMemory}）本就持有 Mapper，顺带实现本接口；
 * 装饰器经 {@code ObjectProvider} 惰性取用——取不到就退回纯 {@code log.warn}（对现有部署零破坏）。
 *
 * @author nebula
 */
public interface VectorReindexMarker {

    /**
     * 标记一条记忆为「向量待重索引」（向量写入/删除失败时调用）。
     *
     * @param id 记忆 DB 主键
     */
    void markNeedReindex(String id);

    /**
     * 清除一条记忆的「待重索引」标记（对账任务重写向量成功后调用）。
     *
     * @param id 记忆 DB 主键
     */
    void markReindexed(String id);
}
