package com.nebula.common.ai.orchestration;

import com.nebula.common.ai.agent.Agent;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.domain.MemoryType;
import com.nebula.common.ai.memory.AgentMemory;

/**
 * 编排器 Agent
 * 把一次编排执行封装成一个具备身份与记忆的{@link Agent}：以自身功能编码驱动给定
 * {@link OrchestrationGraph}，执行完成后将本次编排轨迹作为一条情景记忆（{@link MemoryType#EPISODIC}）
 * 写入长期记忆，使「编排」与「记忆」统一——编排器自身也能积累历史。
 *
 * <p>记忆门面可为 null（未配置记忆时），此时仅执行不落记忆。记忆的归属编码由门面按其
 * {@code AgentMemoryConfig} 注入，应与本 Agent 的功能编码一致。
 *
 * @author nebula
 */
public class OrchestrationAgent implements Agent {

    private final String agentCode;

    private final AgentMemory memory;

    private final Orchestrator orchestrator;

    private final OrchestrationGraph graph;

    public OrchestrationAgent(String agentCode, AgentMemory memory, Orchestrator orchestrator, OrchestrationGraph graph) {
        if (agentCode == null || agentCode.isEmpty()) {
            throw new IllegalArgumentException("agentCode不能为空");
        }
        if (orchestrator == null || graph == null) {
            throw new IllegalArgumentException("orchestrator与graph不能为空");
        }
        this.agentCode = agentCode;
        this.memory = memory;
        this.orchestrator = orchestrator;
        this.graph = graph;
    }

    @Override
    public String agentCode() {
        return agentCode;
    }

    @Override
    public AgentMemory memory() {
        return memory;
    }

    /**
     * 执行编排，并在完成后落一条编排轨迹记忆
     *
     * @param ctx 编排共享上下文（可为 null，内部会新建）
     * @return 执行后的上下文
     */
    public OrchestrationContext run(OrchestrationContext ctx) {
        OrchestrationContext context = ctx == null ? new OrchestrationContext() : ctx;
        OrchestrationContext result = orchestrator.run(graph, context);
        rememberTrace(result);
        return result;
    }

    /**
     * 将本次编排的已执行节点序列作为情景记忆写入长期记忆
     *
     * @param ctx 执行后的上下文
     */
    private void rememberTrace(OrchestrationContext ctx) {
        if (memory == null) {
            return;
        }
        String trace = String.join(" -> ", ctx.nodeResults().keySet());
        MemoryRecord record = new MemoryRecord()
                .setUserId(ctx.getUserId())
                .setConversationId(ctx.getConversationId())
                .setType(MemoryType.EPISODIC)
                .setContent("编排[" + graph.code() + "] 执行轨迹: " + trace);
        memory.remember(record);
    }
}
