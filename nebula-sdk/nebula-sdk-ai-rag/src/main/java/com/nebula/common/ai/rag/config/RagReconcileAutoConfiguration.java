package com.nebula.common.ai.rag.config;

import com.nebula.common.ai.api.VectorReindexMarker;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.memory.MemoryReindexReconciler;
import com.nebula.common.ai.rag.memory.VectorLongTermMemory;
import com.nebula.common.ai.rag.memory.store.AiMemoryMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 记忆向量对账自动装配（批次4 need_reindex 补偿）。
 *
 * <p>仅当 {@code nebula.ai.rag.reconcile.enabled=true} 且向量记忆链路就绪（{@link VectorLongTermMemory} +
 * {@link VectorReindexMarker}）时，注册 {@link MemoryReindexReconciler}。对账依赖向量装饰器
 * （mode=vector 才有双写不一致问题），故这些 Bean 缺任一即不装配——db 模式下无对账必要。
 *
 * <p>{@link AiMemoryMapper} 只作构造参数注入、<b>不进 {@code @ConditionalOnBean}</b>：Mapper 的 BeanDefinition 由
 * {@code MapperScannerConfigurer} 在 {@code ConfigurationClassPostProcessor} 之后才注册，而自动装配的条件在此之前
 * 就已求值，把 Mapper 写进条件会让它恒为假、整个 Bean 被静默跳过（详见 {@code HarnessAutoConfiguration} 类注释）。
 *
 * <p><b>调度前提</b>：{@link MemoryReindexReconciler} 的 {@code @Scheduled} 需宿主开启 {@code @EnableScheduling} 才触发
 * （SDK 遵循「不自作主张开调度」约定）。manager 启动类已开启，blog 若需对账须自行开启。装配在
 * {@link MemoryVectorAutoConfiguration} 之后，确保向量装饰器先就绪。
 *
 * @author nebula
 */
@AutoConfiguration(after = MemoryVectorAutoConfiguration.class)
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "nebula.ai.rag.reconcile", name = "enabled", havingValue = "true")
public class RagReconcileAutoConfiguration {

    /**
     * 记忆向量对账任务。
     *
     * @param aiMemoryMapper       记忆 Mapper（扫 need_reindex）
     * @param vectorLongTermMemory 向量装饰器（补写向量）
     * @param reindexMarker        重索引标记器（清标记）
     * @param properties           AI 配置属性
     * @return 对账任务
     */
    @Bean
    @ConditionalOnBean({VectorLongTermMemory.class, VectorReindexMarker.class})
    public MemoryReindexReconciler memoryReindexReconciler(AiMemoryMapper aiMemoryMapper,
                                                           VectorLongTermMemory vectorLongTermMemory,
                                                           VectorReindexMarker reindexMarker,
                                                           AiProperties properties) {
        return new MemoryReindexReconciler(aiMemoryMapper, vectorLongTermMemory,
                reindexMarker, properties.getRag().getReconcile());
    }
}
