package com.nebula.common.ai.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Agent 定义仓储
 * SDK 默认实现，支持编程式注册。无存储依赖，便于无数据库场景与单元测试；业务侧可声明数据库实现覆盖。
 * 以 {@code (agentCode, version)} 为键，{@link #find(String)} 取该 agentCode 下版本号最大的启用定义。
 *
 * @author nebula
 */
public class InMemoryAgentDefinitionRepository implements AgentDefinitionRepository {

    private final Map<String, AgentDefinition> agents = new ConcurrentHashMap<>();

    /**
     * 注册一个 Agent 定义（键为 agentCode:version）
     *
     * @param definition Agent 定义
     * @return 当前仓储，便于链式注册
     */
    public InMemoryAgentDefinitionRepository register(AgentDefinition definition) {
        if (definition != null && definition.getAgentCode() != null) {
            agents.put(key(definition.getAgentCode(), definition.getVersion()), definition);
        }
        return this;
    }

    @Override
    public AgentDefinition find(String agentCode) {
        if (agentCode == null) {
            return null;
        }
        // 取同 agentCode 下版本号最大的定义
        return agents.values().stream()
                .filter(a -> agentCode.equals(a.getAgentCode()))
                .max((x, y) -> Integer.compare(x.getVersion(), y.getVersion()))
                .orElse(null);
    }

    @Override
    public AgentDefinition find(String agentCode, int version) {
        return agentCode == null ? null : agents.get(key(agentCode, version));
    }

    private String key(String agentCode, int version) {
        return agentCode + ":" + version;
    }
}
