package com.nebula.common.ai.flow.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.agent.AgentDefinitionRepository;
import com.nebula.common.ai.agent.AgentEngine;
import com.nebula.common.ai.agent.AgentInstanceSnapshot;
import com.nebula.common.ai.agent.AgentInstanceStore;
import com.nebula.common.ai.config.FlowAutoConfiguration;
import com.nebula.common.ai.iteration.IterationChainStore;
import com.nebula.common.ai.iteration.IterationDriver;
import com.nebula.common.ai.iteration.IterationLock;
import com.nebula.common.ai.orchestration.RunStateStore;
import com.nebula.common.ai.webhook.WebhookDeliveryStore;
import com.nebula.common.ai.webhook.WebhookDispatcher;
import com.nebula.common.ai.webhook.WebhookRetryDriver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * AI流程落库层自动装配
 * 将三张流程表的 Mapper（{@code com.nebula.common.ai.flow.store} 包）注册到容器，并以数据库版
 * {@link DatabaseFlowDefinitionRepository} 顶替 SDK {@code FlowAutoConfiguration} 提供的默认内存仓储
 * {@code InMemoryFlowDefinitionRepository}（后者为 {@code @ConditionalOnMissingBean(FlowDefinitionRepository.class)}）。
 *
 * <p>仅在具备 MyBatis 运行时（classpath 存在 {@link SqlSessionFactory}）时装配，避免无数据源的模块误装。
 * 业务服务只需依赖本模块即可获得「DB 驱动的流程定义」，无需在各自启动类上额外 {@code @MapperScan} 本包。
 *
 * @author nebula
 */
@AutoConfiguration(before = FlowAutoConfiguration.class)
@ConditionalOnClass(SqlSessionFactory.class)
@MapperScan("com.nebula.common.ai.flow.store")
public class AiFlowStoreAutoConfiguration {

    /**
     * 数据库版流程定义仓储，从三表组装 FlowDefinition 供 FlowEngine 运行
     *
     * @param flowMapper 流程头 Mapper
     * @param nodeMapper 节点 Mapper
     * @param edgeMapper 边 Mapper
     * @return 数据库版流程定义仓储
     */
    @Bean
    @ConditionalOnMissingBean
    public DatabaseFlowDefinitionRepository databaseFlowDefinitionRepository(AiFlowMapper flowMapper,
                                                                             AiFlowNodeMapper nodeMapper,
                                                                             AiFlowEdgeMapper edgeMapper) {
        return new DatabaseFlowDefinitionRepository(flowMapper, nodeMapper, edgeMapper);
    }

    /**
     * 数据库版模型档案仓储，从 {@code ai_model_profile} 表按编码加载并解密组装为 ModelProfile，
     * 覆盖 SDK 默认内存实现。apiKey 加解密密钥取自配置 {@code nebula.ai.profile.secret}（含默认值）。
     *
     * @param profileMapper 模型档案 Mapper
     * @param objectMapper  JSON 处理器（缺省自建）
     * @param environment   环境，用于读取加解密密钥
     * @return 数据库版模型档案仓储
     */
    @Bean
    @ConditionalOnMissingBean
    public DatabaseModelProfileRepository databaseModelProfileRepository(AiModelProfileMapper profileMapper,
                                                                         ObjectProvider<ObjectMapper> objectMapper,
                                                                         Environment environment) {
        String secret = environment.getProperty("nebula.ai.profile.secret", "nebula-ai-profile-default-secret");
        return new DatabaseModelProfileRepository(profileMapper, objectMapper.getIfAvailable(ObjectMapper::new), secret);
    }

    /**
     * 数据库版编排执行状态存储，落 ai_flow_run / ai_flow_run_node 两表，使 FlowEngine 支持断点续跑。
     * 装配后由 SDK 侧 FlowEngine/DagOrchestrator 经 {@code ObjectProvider} 注入；缺失则编排退化为纯内存执行。
     *
     * @param runMapper     执行实例 Mapper
     * @param runNodeMapper 执行节点轨迹 Mapper
     * @return 数据库版编排执行状态存储
     */
    @Bean
    @ConditionalOnMissingBean
    public RunStateStore runStateStore(AiFlowRunMapper runMapper, AiFlowRunNodeMapper runNodeMapper) {
        return new DatabaseRunStateStore(runMapper, runNodeMapper);
    }

    /**
     * 数据库版 Agent 定义仓储，读 {@code ai_agent} 表按 agentCode 组装 AgentDefinition，覆盖 SDK 默认内存实现。
     * 支撑 1 Flow : N Agent 复用。
     *
     * @param agentMapper Agent 定义 Mapper
     * @return 数据库版 Agent 定义仓储
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentDefinitionRepository agentDefinitionRepository(AiAgentMapper agentMapper) {
        return new DatabaseAgentDefinitionRepository(agentMapper);
    }

    /**
     * 数据库版 Agent 实例存储，落 {@code ai_agent_instance} 两表，使 AgentEngine 支持实例回放 / 续跑。
     * 装配后由 SDK 侧 AgentEngine 经 {@code ObjectProvider} 注入；缺失则 AgentEngine 退化为纯内存执行。
     *
     * @param instanceMapper   实例头 Mapper
     * @param transitionMapper 转移轨迹 Mapper
     * @return 数据库版 Agent 实例存储
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentInstanceStore agentInstanceStore(AiAgentInstanceMapper instanceMapper,
                                                 AiAgentInstanceTransitionMapper transitionMapper) {
        return new DatabaseAgentInstanceStore(instanceMapper, transitionMapper);
    }

    /**
     * 数据库版迭代链存储，落 {@code ai_agent_iteration} 表，支撑跨实例递推（系列逐轮生成）。
     * 与 {@link DatabaseAgentInstanceStore} 同构；缺失则 {@link IterationDriver} 无链可推进。
     *
     * @param iterationMapper 迭代链 Mapper
     * @return 数据库版迭代链存储
     */
    @Bean
    @ConditionalOnMissingBean
    public IterationChainStore iterationChainStore(AiAgentIterationMapper iterationMapper) {
        return new DatabaseIterationChainStore(iterationMapper);
    }

    /**
     * 数据库版回调投递记录存储，落 {@code ai_webhook_delivery}（日志 + 断点续发，见 docs/编排回调Webhook设计.md）。
     *
     * @param deliveryMapper 投递记录 Mapper
     * @return 数据库版投递记录存储
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookDeliveryStore webhookDeliveryStore(AiWebhookDeliveryMapper deliveryMapper) {
        return new DatabaseWebhookDeliveryStore(deliveryMapper);
    }

    /**
     * HTTP 版回调投递器：把回调 body POST 到目标 URL（复用 {@code CloseableHttpClient}）。失败落 FAILED 记录、
     * 不抛异常、不阻塞主流程。若无 HTTP 客户端（未装 sdk-ai 的 AiAutoConfiguration）则退化为 {@link WebhookDispatcher#NOOP}。
     *
     * @param deliveryStore     投递记录存储
     * @param httpClientProvider HTTP 客户端（缺省来自 sdk-ai）
     * @param objectMapper      JSON 处理器
     * @return 回调投递器
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookDispatcher webhookDispatcher(WebhookDeliveryStore deliveryStore,
                                               ObjectProvider<CloseableHttpClient> httpClientProvider,
                                               ObjectProvider<ObjectMapper> objectMapper) {
        CloseableHttpClient httpClient = httpClientProvider.getIfAvailable();
        if (httpClient == null) {
            return WebhookDispatcher.NOOP;
        }
        return new HttpWebhookDispatcher(deliveryStore, httpClient, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    /**
     * 回调重发驱动（W2 自动重发）：扫出到点可重发的失败投递逐个 redeliver。纯业务 {@code retryDue}，
     * 定时触发由业务服务的 {@code @Scheduled} 持有（与 {@link IterationDriver} 同一分层）。
     *
     * @param deliveryStore 投递记录存储
     * @param dispatcher    回调投递器
     * @return 重发驱动
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookRetryDriver webhookRetryDriver(WebhookDeliveryStore deliveryStore,
                                                 WebhookDispatcher dispatcher) {
        return new WebhookRetryDriver(deliveryStore, dispatcher);
    }

    /**
     * 把回调投递器接线进 {@link AgentEngine}（流程级实例回调 W2）。AgentEngine bean 在 sdk-ai 的
     * FlowAutoConfiguration 装配，WebhookDispatcher 在本模块——sdk-ai 反向不依赖本模块，故在此接线：
     * 返回标记 bean，构造时把 dispatcher set 进已存在的 AgentEngine。
     *
     * @param agentEngine 已装配的 Agent 执行门面
     * @param dispatcher  回调投递器
     * @return 接线标记 bean
     */
    @Bean
    public Object agentEngineWebhookWiring(AgentEngine agentEngine, WebhookDispatcher dispatcher) {
        agentEngine.setWebhookDispatcher(dispatcher);
        return new Object();
    }

    /**
     * 跨实例迭代驱动：把一次次 Agent 执行串成链，上一轮产出成为下一轮输入（见 docs/跨实例迭代层设计.md）。
     * 只负责纯业务的 {@code tick()}（扫链→抢锁→组装→跑→回写）；<b>定时触发由业务服务的 {@code @Scheduled} 持有</b>，
     * <b>分布式抢占由 {@link IterationLock} 决定</b>（单实例缺省 NOOP，多实例由业务模块提供 Redis 实现）。
     *
     * <p>上一轮产物读取器注入为 {@code id -> instanceStore.load(id).contextSnapshot()}——把对 {@link AgentInstanceStore}
     * 的依赖外置，SDK 侧 {@code IterationDriver} 不直接耦合实例存储。
     *
     * @param chainStore           迭代链存储
     * @param agentEngine          Agent 执行门面
     * @param definitionRepository Agent 定义仓储（按 agentCode 取定义）
     * @param instanceStore        Agent 实例存储（读上一轮产物）
     * @param lockProvider         推进锁（缺省 NOOP，多实例注入 Redis 实现覆盖）
     * @return 迭代驱动
     */
    @Bean
    @ConditionalOnMissingBean
    public IterationDriver iterationDriver(IterationChainStore chainStore,
                                           AgentEngine agentEngine,
                                           AgentDefinitionRepository definitionRepository,
                                           AgentInstanceStore instanceStore,
                                           ObjectProvider<IterationLock> lockProvider,
                                           ObjectProvider<WebhookDispatcher> webhookDispatcher) {
        IterationDriver driver = new IterationDriver(chainStore,
                lockProvider.getIfAvailable(() -> IterationLock.NOOP),
                agentEngine, definitionRepository);
        // 上一轮产物 = 该实例终态 context 快照（markTerminal 已全量刷）
        driver.setPreviousOutputsLoader(instanceId -> {
            AgentInstanceSnapshot snapshot = instanceStore.load(instanceId);
            return snapshot == null ? null : snapshot.contextSnapshot();
        });
        // 每轮 advance 成功后触发回调（缺省 NOOP，无回调基建时静默跳过）
        driver.setWebhookDispatcher(webhookDispatcher.getIfAvailable(() -> WebhookDispatcher.NOOP));
        return driver;
    }
}
