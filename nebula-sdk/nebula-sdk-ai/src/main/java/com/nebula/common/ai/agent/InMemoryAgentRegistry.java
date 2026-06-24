package com.nebula.common.ai.agent;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的Agent注册表
 *
 * @author nebula
 */
public class InMemoryAgentRegistry implements AgentRegistry {

    private final Map<String, Agent> registry = new ConcurrentHashMap<>();

    @Override
    public void register(Agent agent) {
        if (agent == null || agent.agentCode() == null || agent.agentCode().isEmpty()) {
            throw new IllegalArgumentException("Agent与agentCode不能为空");
        }
        registry.put(agent.agentCode(), agent);
    }

    @Override
    public Agent get(String agentCode) {
        Agent agent = registry.get(agentCode);
        if (agent == null) {
            throw new IllegalArgumentException("未注册的agentCode: " + agentCode);
        }
        return agent;
    }

    @Override
    public boolean contains(String agentCode) {
        return registry.containsKey(agentCode);
    }

    @Override
    public Collection<Agent> all() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
