package com.nebula.common.ai.agent;

import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.memory.AgentMemory;

import java.util.Map;

/**
 * Agent抽象基类
 * 持有Agent功能编码、记忆门面与AI调用基建，并在发起调用前自动把本Agent的功能编码
 * 注入{@link AiRequest}，使下游的记忆隔离与日志归集都能按Agent区分。
 *
 * @author nebula
 */
public abstract class AbstractAgent implements Agent {

    private final String agentCode;

    private final AgentMemory memory;

    private final AiService aiService;

    protected AbstractAgent(String agentCode, AgentMemory memory, AiService aiService) {
        if (agentCode == null || agentCode.isEmpty()) {
            throw new IllegalArgumentException("agentCode不能为空");
        }
        this.agentCode = agentCode;
        this.memory = memory;
        this.aiService = aiService;
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
     * 获取AI服务，供子类按需直接使用
     *
     * @return AI服务
     */
    protected AiService aiService() {
        return aiService;
    }

    /**
     * 发起对话调用
     * 调用前自动将本Agent的功能编码注入请求，再委托{@link AiService#chat(AiRequest)}。
     *
     * @param request 请求参数
     * @return 响应结果
     */
    protected Map<String, Object> chat(AiRequest request) {
        request.setAgentCode(agentCode);
        return aiService.chat(request);
    }
}
