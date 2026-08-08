package com.nebula.common.ai.harness.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.harness.draft.DraftStore;
import com.nebula.common.ai.harness.draft.FlowDefinitionCodec;
import com.nebula.common.ai.harness.realrun.DraftConfirmationStore;
import com.nebula.common.ai.harness.realrun.DraftRealRunService;
import com.nebula.common.ai.harness.realrun.DraftRunner;
import com.nebula.common.ai.harness.realrun.HarnessOperationStore;
import com.nebula.common.ai.harness.simulate.SimulationInputValidator;
import com.nebula.common.ai.harness.tool.GetHarnessOperationToolDefinition;
import com.nebula.common.ai.harness.tool.RealRunDraftToolDefinition;
import com.nebula.common.ai.harness.validate.DraftValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Harness B4 真实试跑自动装配。
 *
 * <p>独立于基础 Harness 配置，确保条件评估发生在草稿、确认和 operation Store Bean 已注册之后。
 *
 * @author nebula
 */
@AutoConfiguration(after = HarnessAutoConfiguration.class)
@ConditionalOnBean({DraftStore.class, DraftConfirmationStore.class, HarnessOperationStore.class,
        DraftRunner.class, DraftValidator.class, SimulationInputValidator.class, FlowDefinitionCodec.class})
public class HarnessRealRunAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DraftRealRunService draftRealRunService(
            DraftStore draftStore,
            DraftConfirmationStore confirmationStore,
            HarnessOperationStore operationStore,
            DraftRunner runner,
            DraftValidator validator,
            SimulationInputValidator inputValidator,
            FlowDefinitionCodec codec,
            ObjectProvider<ObjectMapper> objectMapper,
            HarnessRealRunProperties properties,
            @Qualifier("harnessRealRunExecutor") ExecutorService operationExecutor,
            @Qualifier("harnessRealRunHeartbeatExecutor") ScheduledExecutorService heartbeatExecutor) {
        return new DraftRealRunService(
                draftStore, confirmationStore, operationStore, runner, validator, inputValidator, codec,
                objectMapper.getIfAvailable(ObjectMapper::new), properties, operationExecutor, heartbeatExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public RealRunDraftToolDefinition realRunDraftToolDefinition(DraftRealRunService service) {
        return new RealRunDraftToolDefinition(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public GetHarnessOperationToolDefinition getHarnessOperationToolDefinition(DraftRealRunService service) {
        return new GetHarnessOperationToolDefinition(service);
    }
}
