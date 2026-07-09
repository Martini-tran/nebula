package com.nebula.common.ai.memory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的Agent记忆注册表
 *
 * @author nebula
 */
public class InMemoryAgentMemoryRegistry implements AgentMemoryRegistry {

    private final Map<String, AgentMemory> registry = new ConcurrentHashMap<>();

    @Override
    public void register(AgentMemory agentMemory) {
        if (agentMemory == null || agentMemory.agentCode() == null || agentMemory.agentCode().isEmpty()) {
            throw new IllegalArgumentException("AgentMemory与agentCode不能为空");
        }
        registry.put(agentMemory.agentCode(), agentMemory);
    }

    @Override
    public AgentMemory get(String agentCode) {
        AgentMemory agentMemory = registry.get(agentCode);
        if (agentMemory == null) {
            throw new IllegalArgumentException("未注册的agentCode: " + agentCode);
        }
        return agentMemory;
    }

    @Override
    public boolean contains(String agentCode) {
        return registry.containsKey(agentCode);
    }

    @Override
    public Collection<AgentMemory> all() {
        return Collections.unmodifiableCollection(registry.values());
    }
}
