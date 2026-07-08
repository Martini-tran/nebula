package com.nebula.common.ai.iteration;

import com.nebula.common.ai.agent.AgentDefinition;
import com.nebula.common.ai.agent.AgentDefinitionRepository;
import com.nebula.common.ai.agent.AgentEngine;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.statemachine.StateMachineOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 跨实例迭代驱动
 * "执行与执行之间"的递推引擎：把一次次 {@link AgentEngine} 执行串成时间线上的链，<b>上一轮的产出成为下一轮的输入</b>
 * （见 docs/跨实例迭代层设计.md）。它是<b>编排层</b>而非内核——不进状态机、不受"节点只写 context"铁律约束，
 * 用最普通的方式读上一轮实例产物、组装本轮入参、调引擎、回写链。Agent 对"自己在一条链里"完全无感知。
 *
 * <p><b>与定时/锁解耦</b>：本类只暴露纯业务的 {@link #tick(LocalDateTime)}（扫链→抢锁→组装→跑→回写），
 * {@code @Scheduled} 触发由外层业务模块持有（SDK 不自作主张开 {@code @EnableScheduling}）；分布式抢占由注入的
 * {@link IterationLock} 决定（单实例 NOOP / 多实例 Redis）。
 *
 * <p><b>落库三次、角色分明</b>（回答"怎么落库"）：①实例本身、②单篇产物由 {@code AgentEngine} 内部自动落
 * （{@code instanceStore}）；③链的推进由本类用 {@link IterationChainStore#advance} 落——<b>不靠 Agent 内工具/回调</b>。
 *
 * @author nebula
 */
public class IterationDriver {

    private static final Logger log = LoggerFactory.getLogger(IterationDriver.class);

    /**
     * 单次 tick 最多推进多少条链，防一次扫太多拖垮线程
     */
    private static final int DEFAULT_BATCH = 50;

    /**
     * 推进锁的持有上限：一轮实例（含 LLM 多轮）通常分钟级，给 10 分钟兜底崩溃自愈
     */
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    /**
     * 连续失败达此值自动 PAUSED，防定时打空转
     */
    private static final int PAUSE_THRESHOLD = 3;

    private final IterationChainStore chainStore;

    private final IterationLock lock;

    private final AgentEngine agentEngine;

    private final AgentDefinitionRepository definitionRepository;

    /**
     * until 条件求值：复用与流程边一致的 SpEL 编译器，以本轮产出的 context 为根对象
     */
    private final ConditionCompiler conditionCompiler = new ConditionCompiler();

    public IterationDriver(IterationChainStore chainStore,
                           IterationLock lock,
                           AgentEngine agentEngine,
                           AgentDefinitionRepository definitionRepository) {
        this.chainStore = chainStore;
        this.lock = lock == null ? IterationLock.NOOP : lock;
        this.agentEngine = agentEngine;
        this.definitionRepository = definitionRepository;
    }

    /**
     * 扫描到点的链并各推进一轮。由外层 {@code @Scheduled} 定时调用（多实例都调，靠 {@link IterationLock} 去重）。
     *
     * @param now 当前时间（显式传入便于测试）
     * @return 本次实际推进（无论成败）的链数
     */
    public int tick(LocalDateTime now) {
        if (chainStore == null) {
            return 0;
        }
        List<IterationChain> due = chainStore.findDue(now, DEFAULT_BATCH);
        int advanced = 0;
        for (IterationChain chain : due) {
            // ② 抢占本轮推进权：抢不到说明别的节点在跑这轮，跳过（多实例防重跑）
            if (!lock.tryAcquire(chain.chainId(), LOCK_TTL)) {
                log.debug("链[{}]本轮已被其它节点抢占，跳过", chain.chainId());
                continue;
            }
            try {
                advanceOne(chain, now);
                advanced++;
            } catch (RuntimeException e) {
                // 单条链推进失败不影响同批其它链
                log.error("链[{}]推进异常: {}", chain.chainId(), e.getMessage(), e);
                safeMarkFailure(chain, e.getMessage(), now);
            } finally {
                lock.release(chain.chainId());
            }
        }
        return advanced;
    }

    /**
     * 推进单条链一轮：组装 inputs → 跑实例 → 判 until → 回写链。
     */
    private void advanceOne(IterationChain chain, LocalDateTime now) {
        AgentDefinition def = definitionRepository == null ? null : definitionRepository.find(chain.agentCode());
        if (def == null) {
            markFailure(chain, "链引用的 Agent 不存在: " + chain.agentCode(), now);
            return;
        }

        // ③ 组装本轮 inputs：首轮用 seedInputs；后续轮读上一轮实例产物按 carryOver 映射
        Map<String, Object> inputs = assembleInputs(chain);

        // ④ 调引擎跑一轮（引擎内部自动落 ①实例 ②产物；本类不碰实例表）
        OrchestrationContext ctx = agentEngine.run(def, inputs, chain.userId(), chain.conversationId());

        // 实例失败：不推进 seq，累计失败（达阈值自动 PAUSED），下轮重试
        if (ctx.contains(StateMachineOrchestrator.FAILED_ERROR_KEY)) {
            String err = String.valueOf(ctx.get(StateMachineOrchestrator.FAILED_ERROR_KEY));
            markFailure(chain, "本轮实例失败: " + err, now);
            return;
        }
        // 实例挂起（阶段 3 能力）：本轮未产出终态产物，不推进 seq，交由 signal 唤醒后由业务另行处理
        if (ctx.contains(StateMachineOrchestrator.SUSPENDED_KEY)) {
            log.info("链[{}]本轮实例挂起，暂不推进 seq（等唤醒）", chain.chainId());
            markFailure(chain, "本轮实例挂起未产出，等待唤醒", now);
            return;
        }

        String newInstanceId = ctx.getString(AgentEngine.CURRENT_INSTANCE_KEY);

        // ⑤ 判出链：until 条件满足 或 达轮次上限 → COMPLETED，否则 ACTIVE
        boolean done = untilSatisfied(chain, ctx) || reachedMax(chain);
        String newStatus = done ? "COMPLETED" : "ACTIVE";
        LocalDateTime nextRunAt = done ? now : nextCronTime(chain.cron(), now);

        // ⑥ 回写链（幂等 CAS）：影响 0 行 = 已被别的节点推进过，放弃（多实例双保险）
        boolean ok = chainStore.advance(chain.chainId(), chain.seq(), newInstanceId, nextRunAt, newStatus);
        if (!ok) {
            log.warn("链[{}]advance 未命中（seq={} 已被推进过），放弃本轮回写", chain.chainId(), chain.seq());
            return;
        }
        log.info("链[{}]推进第 {} 轮 → 实例[{}]，status={}，下轮={}",
                chain.chainId(), chain.seq() + 1, newInstanceId, newStatus, done ? "-" : nextRunAt);
    }

    /**
     * 组装本轮入参：首轮 = seedInputs；后续轮 = 读上一轮实例产物按 carryOver 映射，再与 seedInputs 浅合并
     * （carry-over 值优先，覆盖 seed 里同名的历史常量键）。
     */
    private Map<String, Object> assembleInputs(IterationChain chain) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        // seed 恒定入参（如系列主题）先铺底，后续轮也保留
        if (chain.seedInputs() != null) {
            inputs.putAll(chain.seedInputs());
        }
        if (chain.isFirstRound()) {
            return inputs;
        }
        // 后续轮：读上一轮产物，按 carryOver 映射覆盖
        Map<String, Object> prev = loadPreviousOutputs(chain.lastInstanceId());
        Map<String, String> carry = chain.carryOver();
        if (carry != null && !carry.isEmpty() && prev != null) {
            carry.forEach((fromKey, toKey) -> {
                Object v = prev.get(fromKey);
                if (v != null) {
                    inputs.put(toKey, v);
                }
            });
        }
        // 附带当前轮次，供 Agent 感知"这是第几篇"
        inputs.put("prevSeq", chain.seq());
        return inputs;
    }

    /**
     * 读上一轮实例的产物：取其终态 contextSnapshot（markTerminal 已全量刷）。
     * 通过 AgentEngine 复用的 AgentInstanceStore 读，无需额外接口——这里用 resume 的只读能力拿快照的 context。
     * <p>为不给 IterationDriver 强耦合 AgentInstanceStore，改由 {@link #previousOutputsLoader} 注入读取逻辑。
     */
    private Map<String, Object> loadPreviousOutputs(String lastInstanceId) {
        if (previousOutputsLoader == null) {
            log.warn("未配置上一轮产物读取器，carry-over 无数据源（链退化为每轮独立跑）");
            return Map.of();
        }
        Map<String, Object> out = previousOutputsLoader.apply(lastInstanceId);
        return out == null ? Map.of() : out;
    }

    /**
     * 上一轮产物读取器：给定实例 id，返回其终态 context 快照（= 产物）。由装配方注入
     * （通常 {@code id -> instanceStore.load(id).contextSnapshot()}），把对 AgentInstanceStore 的依赖外置。
     */
    private java.util.function.Function<String, Map<String, Object>> previousOutputsLoader;

    /**
     * 注入上一轮产物读取器（装配时调用一次）。
     *
     * @param loader 实例 id → 终态 context 快照
     */
    public void setPreviousOutputsLoader(java.util.function.Function<String, Map<String, Object>> loader) {
        this.previousOutputsLoader = loader;
    }

    /**
     * until 条件是否满足：空表达式恒 false（只靠 maxIterations 收尾）；否则以本轮 context 求值。
     */
    private boolean untilSatisfied(IterationChain chain, OrchestrationContext ctx) {
        String expr = chain.untilExpr();
        if (expr == null || expr.isBlank()) {
            return false;
        }
        try {
            Predicate<OrchestrationContext> predicate = conditionCompiler.compile(expr);
            return predicate != null && predicate.test(ctx);
        } catch (RuntimeException e) {
            // until 表达式坏了不该让链无限跑，也不该炸——记警告，视为未满足，靠 maxIterations 兜底
            log.warn("链[{}]until 表达式[{}]求值失败（视为未满足）: {}", chain.chainId(), expr, e.getMessage());
            return false;
        }
    }

    /**
     * 是否达轮次上限（本轮完成后 seq+1 >= max）。
     */
    private boolean reachedMax(IterationChain chain) {
        Integer max = chain.maxIterations();
        return max != null && chain.seq() + 1 >= max;
    }

    /**
     * 按 cron 算下一次触发时间；cron 非法则顺延 1 天兜底（不让链卡死）。
     */
    private LocalDateTime nextCronTime(String cron, LocalDateTime from) {
        try {
            CronExpression expr = CronExpression.parse(cron);
            LocalDateTime next = expr.next(from);
            return next == null ? from.plusDays(1) : next;
        } catch (RuntimeException e) {
            log.warn("cron[{}]解析失败，下一轮顺延 1 天兜底: {}", cron, e.getMessage());
            return from.plusDays(1);
        }
    }

    private void markFailure(IterationChain chain, String error, LocalDateTime now) {
        chainStore.markFailure(chain.chainId(), error, PAUSE_THRESHOLD, nextCronTime(chain.cron(), now));
    }

    private void safeMarkFailure(IterationChain chain, String error, LocalDateTime now) {
        try {
            markFailure(chain, error, now);
        } catch (RuntimeException ignore) {
            log.error("链[{}]记失败也异常，跳过", chain.chainId());
        }
    }
}
