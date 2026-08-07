package com.nebula.manager.ai.copilot;

import com.nebula.common.ai.harness.conversation.HarnessCallContext;
import com.nebula.common.ai.harness.conversation.HarnessExampleProvider;
import com.nebula.common.ai.harness.conversation.HarnessRequest;
import com.nebula.common.ai.properties.AiProperties;
import com.nebula.common.ai.rag.flowexample.FlowExample;
import com.nebula.common.ai.rag.flowexample.FlowExampleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manager 对 Flow Harness 的 few-shot 召回适配器。
 *
 * <p>向量能力未装配、无命中或召回故障时返回空集合，增强层不会阻断生成主链路。
 *
 * @author nebula
 */
@Slf4j
@Component
public class CopilotHarnessExampleProvider implements HarnessExampleProvider {

    private final ObjectProvider<FlowExampleService> exampleServiceProvider;
    private final AiProperties.Rag.FewShot config;

    public CopilotHarnessExampleProvider(ObjectProvider<FlowExampleService> exampleServiceProvider,
                                         AiProperties aiProperties) {
        this.exampleServiceProvider = exampleServiceProvider;
        AiProperties properties = aiProperties == null ? new AiProperties() : aiProperties;
        this.config = properties.getRag().getFewShot();
    }

    @Override
    public List<String> examplePrompts(HarnessRequest request, HarnessCallContext context) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            return List.of();
        }
        FlowExampleService service = exampleServiceProvider.getIfAvailable();
        if (service == null) {
            return List.of();
        }
        try {
            List<FlowExample> examples = service.recall(
                    request.prompt(), config.getTopK(), config.getMinScore());
            if (examples.isEmpty()) {
                return List.of();
            }
            return List.of(format(examples));
        } catch (RuntimeException e) {
            log.warn("Copilot few-shot 召回失败，已降级为零样本: requestId={}, error={}",
                    context.requestId(), e.getMessage());
            return List.of();
        }
    }

    private String format(List<FlowExample> examples) {
        StringBuilder prompt = new StringBuilder(
                "以下是与当前需求相似的已有流程，供你参考其结构与命名（不必照搬，按当前需求裁剪）：\n");
        for (FlowExample example : examples) {
            prompt.append("- ").append(example.name() == null ? example.flowCode() : example.name());
            if (example.description() != null && !example.description().isBlank()) {
                prompt.append("：").append(example.description());
            }
            prompt.append('\n');
        }
        return prompt.toString();
    }
}
