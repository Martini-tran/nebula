package com.nebula.common.ai.agent;

import com.nebula.common.ai.memory.AgentMemory;

/**
 * Agent最小身份契约
 * 每个Agent由一个全局唯一的功能编码标识，并持有按该编码隔离的{@link AgentMemory}。
 * 本接口不统一业务方法签名——不同Agent功能各异，具体业务能力由各自的子接口定义。
 *
 * @author nebula
 */
public interface Agent {

    /**
     * 获取Agent功能编码
     *
     * @return Agent功能编码
     */
    String agentCode();

    /**
     * 获取该Agent的记忆门面
     *
     * @return 记忆门面
     */
    AgentMemory memory();
}
