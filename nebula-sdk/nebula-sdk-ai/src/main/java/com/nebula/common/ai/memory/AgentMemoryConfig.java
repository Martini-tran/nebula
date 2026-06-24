package com.nebula.common.ai.memory;

import com.nebula.common.ai.domain.MemoryType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 单个Agent的记忆配置
 * 不同功能的Agent可拥有不同的记忆能力：短期窗口策略、是否启用长期记忆、启用哪些长期记忆类型。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class AgentMemoryConfig {

    /**
     * Agent功能编码
     */
    private String agentCode;

    /**
     * 短期会话历史窗口策略，默认不裁剪
     */
    private MemoryWindow window = NoopMemoryWindow.INSTANCE;

    /**
     * 是否启用长期记忆
     */
    private boolean longTermEnabled = false;

    /**
     * 启用的长期记忆类型，空集合表示不限制（启用全部类型）
     */
    private Set<MemoryType> enabledTypes = Collections.emptySet();

    public AgentMemoryConfig() {
    }

    public AgentMemoryConfig(String agentCode) {
        this.agentCode = agentCode;
    }

    /**
     * 判断指定记忆类型是否被该Agent启用
     *
     * @param type 记忆类型
     * @return 是否启用
     */
    public boolean isTypeEnabled(MemoryType type) {
        return enabledTypes == null || enabledTypes.isEmpty() || enabledTypes.contains(type);
    }

    public AgentMemoryConfig setEnabledTypes(Set<MemoryType> enabledTypes) {
        this.enabledTypes = enabledTypes == null ? Collections.emptySet() : EnumSet.copyOf(enabledTypes);
        return this;
    }

    public AgentMemoryConfig setWindow(MemoryWindow window) {
        this.window = window == null ? NoopMemoryWindow.INSTANCE : window;
        return this;
    }
}
