package com.nebula.common.ai.flow;

import com.nebula.common.ai.agent.tool.ToolCallingService;
import com.nebula.common.ai.domain.AiRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用 ToolCallingService 桩：记录最近一次闭环入参（请求 / 工具白名单 / 工具上下文），返回可配置的终轮响应。
 *
 * @author nebula
 */
class StubToolCallingService implements ToolCallingService {

    AiRequest lastRequest;

    List<String> lastToolCodes;

    ToolContext lastToolContext;

    String responseContent = "STUB";

    /**
     * 置 true 模拟工具调用闭环到达迭代上限被截断
     */
    boolean truncated;

    @Override
    public Map<String, Object> run(AiRequest request, Collection<String> allowedToolCodes, ToolContext toolContext) {
        this.lastRequest = request;
        this.lastToolCodes = allowedToolCodes == null ? null : new ArrayList<>(allowedToolCodes);
        this.lastToolContext = toolContext;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", responseContent);
        response.put("role", "assistant");
        if (truncated) {
            response.put("truncated", true);
        }
        return response;
    }
}
