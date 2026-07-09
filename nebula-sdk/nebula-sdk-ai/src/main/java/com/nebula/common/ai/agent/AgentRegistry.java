package com.nebula.common.ai.agent;

import java.util.Collection;

/**
 * Agent注册表
 * 按Agent功能编码管理并获取对应的{@link Agent}，与{@link com.nebula.common.ai.memory.AgentMemoryRegistry}平行同构。
 *
 * @author nebula
 */
public interface AgentRegistry {

    /**
     * 注册一个Agent
     *
     * @param agent Agent
     */
    void register(Agent agent);

    /**
     * 获取指定功能编码的Agent
     *
     * @param agentCode Agent功能编码
     * @return 对应的Agent
     * @throws IllegalArgumentException 当该编码未注册时
     */
    Agent get(String agentCode);

    /**
     * 判断指定Agent是否已注册
     *
     * @param agentCode Agent功能编码
     * @return 是否已注册
     */
    boolean contains(String agentCode);

    /**
     * 获取全部已注册的Agent
     *
     * @return Agent集合
     */
    Collection<Agent> all();
}
