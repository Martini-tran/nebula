package com.nebula.common.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 记忆策略
 * 解析 {@code ai_agent.memory_config} JSON 段，供 {@code AgentEngine} 的 Import/Export 钩子读取：
 * 实例启动时按 {@link #importKeys} 从长期记忆导入到 context，实例结束时按 {@link #exportStrategy} 写回。
 *
 * <p>结构示例（memory_config）：
 * <pre>{@code
 * { "enabled": true, "import": ["userProfile", "preference"], "exportStrategy": "Append" }
 * }</pre>
 *
 * <p>与 {@link StateConfig} 同风格：{@link #fromMap(Map)} 防御式解析已反序列化的 Map；
 * {@link #DISABLED} 为未配/禁用时的空策略。
 *
 * @author nebula
 */
public final class AgentMemoryPolicy {

    /**
     * 导出策略：覆盖同键 / 追加 / 先 LLM 摘要再写（对应文档四·Export）
     */
    public enum ExportStrategy {
        REPLACE, APPEND, SUMMARY;

        static ExportStrategy parse(Object raw) {
            if (raw == null) {
                return APPEND;
            }
            String s = String.valueOf(raw).trim().toUpperCase();
            try {
                return ExportStrategy.valueOf(s);
            } catch (IllegalArgumentException e) {
                return APPEND;
            }
        }
    }

    /**
     * 未配置 / 禁用记忆时的空策略：不导入、不导出。
     */
    public static final AgentMemoryPolicy DISABLED =
            new AgentMemoryPolicy(false, List.of(), ExportStrategy.APPEND);

    private final boolean enabled;

    private final List<String> importKeys;

    private final ExportStrategy exportStrategy;

    public AgentMemoryPolicy(boolean enabled, List<String> importKeys, ExportStrategy exportStrategy) {
        this.enabled = enabled;
        this.importKeys = importKeys == null ? List.of() : List.copyOf(importKeys);
        this.exportStrategy = exportStrategy == null ? ExportStrategy.APPEND : exportStrategy;
    }

    public boolean enabled() {
        return enabled;
    }

    public List<String> importKeys() {
        return importKeys;
    }

    public ExportStrategy exportStrategy() {
        return exportStrategy;
    }

    /**
     * 从已反序列化的 memory_config Map 解析策略。null / 空 / enabled=false 均归为 {@link #DISABLED}。
     *
     * @param memoryConfig memory_config JSON 反序列化后的 Map
     * @return 记忆策略
     */
    @SuppressWarnings("unchecked")
    public static AgentMemoryPolicy fromMap(Map<String, Object> memoryConfig) {
        if (memoryConfig == null || memoryConfig.isEmpty()) {
            return DISABLED;
        }
        boolean enabled = toBool(memoryConfig.get("enabled"), true);
        if (!enabled) {
            return DISABLED;
        }
        List<String> keys = new ArrayList<>();
        Object importSection = memoryConfig.get("import");
        if (importSection instanceof List<?> list) {
            for (Object k : list) {
                if (k != null) {
                    keys.add(String.valueOf(k));
                }
            }
        }
        ExportStrategy strategy = ExportStrategy.parse(memoryConfig.get("exportStrategy"));
        return new AgentMemoryPolicy(true, keys, strategy);
    }

    private static boolean toBool(Object raw, boolean defaultVal) {
        if (raw == null) {
            return defaultVal;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }
}
