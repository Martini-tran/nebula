package com.nebula.common.ai.memory;

import java.util.Collection;

/**
 * Agent记忆注册表
 * 按Agent功能编码管理并获取对应的{@link AgentMemory}，使每个Agent使用各自独立的记忆配置与隔离空间。
 *
 * @author nebula
 */
public interface AgentMemoryRegistry {

    /**
     * 注册一个Agent记忆
     *
     * @param agentMemory Agent记忆门面
     */
    void register(AgentMemory agentMemory);

    /**
     * 获取指定Agent的记忆门面
     *
     * @param agentCode Agent功能编码
     * @return 对应的记忆门面
     * @throws IllegalArgumentException 当该编码未注册时
     */
    AgentMemory get(String agentCode);

    /**
     * 判断指定Agent是否已注册
     *
     * @param agentCode Agent功能编码
     * @return 是否已注册
     */
    boolean contains(String agentCode);

    /**
     * 获取全部已注册的Agent记忆
     *
     * @return Agent记忆集合
     */
    Collection<AgentMemory> all();
}
