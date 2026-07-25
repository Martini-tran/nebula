package com.nebula.manager.ai.copilot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.ai.flow.store.AiFlow;
import com.nebula.common.ai.flow.store.AiFlowMapper;
import com.nebula.common.ai.rag.flowexample.FlowExampleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Flow few-shot 示例索引器（{@link ApplicationRunner}，场景②）。
 *
 * <p>启动时把 {@code ai_flow} 中已启用的流程「名称 + 描述」向量化进 {@code nebula_flow_example}，供 Flow Copilot 生成前
 * 语义召回作 few-shot 示例。宿主放在 manager 侧——{@code ai_flow} 的读取能力在此，且与召回接线（{@link FlowCopilotService}）
 * 同侧；SDK 的 {@link FlowExampleService} 只提供纯向量能力，不认识业务表。
 *
 * <p>仅当 {@code nebula.ai.rag.fewShot.enabled=true}（{@link FlowExampleService} 装配）时才实际索引，经
 * {@link ObjectProvider} 惰性取——未装配时跳过，不产生任何向量写入。索引失败仅 {@code log.warn} 不阻断启动。
 *
 * <p><b>增量</b>：本批次先做启动全量索引；「流程保存后增量 upsert」作为增强留待后续（在 {@code FlowAdminService} 保存链路
 * 触发 {@link FlowExampleService#upsert}），避免本批次牵扯 flow 保存链路改造。初次上线新增流程需重启索引，可接受。
 *
 * @author nebula
 */
@Slf4j
@Component
public class FlowExampleIndexer implements ApplicationRunner {

    private final AiFlowMapper flowMapper;
    private final ObjectProvider<FlowExampleService> flowExampleServiceProvider;

    public FlowExampleIndexer(AiFlowMapper flowMapper,
                              ObjectProvider<FlowExampleService> flowExampleServiceProvider) {
        this.flowMapper = flowMapper;
        this.flowExampleServiceProvider = flowExampleServiceProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        FlowExampleService service = flowExampleServiceProvider.getIfAvailable();
        if (service == null) {
            // few-shot 未启用（nebula.ai.rag.fewShot.enabled=false），无需索引
            return;
        }
        try {
            List<AiFlow> flows = flowMapper.selectList(new LambdaQueryWrapper<AiFlow>()
                    .eq(AiFlow::getStatus, 1));
            List<FlowExampleService.FlowExampleSource> sources = new ArrayList<>(flows.size());
            for (AiFlow flow : flows) {
                sources.add(new FlowExampleService.FlowExampleSource(
                        flow.getFlowCode(), flow.getName(), flow.getDescription(), null));
            }
            int n = service.indexAll(sources);
            log.info("流程 few-shot 示例向量索引完成：启用流程 {} 个，索引 {} 个", flows.size(), n);
        } catch (Exception e) {
            log.warn("流程 few-shot 示例索引失败（不阻断启动，Copilot 退化为零样本生成）：{}", e.getMessage());
        }
    }
}
