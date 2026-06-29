package com.nebula.common.ai.flow;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表
 * 聚合容器内全部 {@link ToolDefinition} Bean，按 {@code code} 索引，供 {@link ToolNodeExecutor} 运行时按编码取用、
 * 供同步器启动时镜像进 {@code ai_tool} 表。是「代码中有哪些工具」的运行期视图。
 *
 * <p>编码冲突时以先注册者为准（保持与 {@link FlowGraphFactory} 对执行器去重一致的策略），并按
 * {@link ToolDefinition#sortNo()} 升序、再按编码排序，使列表呈现稳定。
 *
 * @author nebula
 */
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    public ToolRegistry(List<ToolDefinition> definitions) {
        if (definitions != null) {
            definitions.stream()
                    .filter(d -> d != null && d.code() != null && !d.code().isBlank())
                    .sorted((a, b) -> {
                        int c = Integer.compare(a.sortNo(), b.sortNo());
                        return c != 0 ? c : a.code().compareTo(b.code());
                    })
                    .forEach(d -> tools.putIfAbsent(d.code(), d));
        }
    }

    /**
     * 按编码取工具
     *
     * @param code 工具编码
     * @return 工具定义，不存在返回 null
     */
    public ToolDefinition find(String code) {
        return code == null ? null : tools.get(code);
    }

    /**
     * 是否存在指定编码的工具
     *
     * @param code 工具编码
     * @return 是否存在
     */
    public boolean contains(String code) {
        return code != null && tools.containsKey(code);
    }

    /**
     * 全部工具（稳定顺序的只读视图）
     *
     * @return 工具集合
     */
    public Collection<ToolDefinition> all() {
        return List.copyOf(tools.values());
    }
}
