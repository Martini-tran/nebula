package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.ContextKeys;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 循环节点驱动器（DAG 内嵌子图循环）
 * LOOP 节点是画布上的「容器」：框选若干成员节点圈成一圈，容器本身不承载业务动作，而是把成员编成一张子图
 * （{@link LoopBody}），按 FOREACH 列表 / COUNT 次数<b>循环执行子图 N 次</b>。这样在 DAG 强制无环的约束下，
 * 用「节点内部循环」实现「按目录逐篇生成」这类遍历语义。
 *
 * <p><b>不实现 {@link FlowNodeExecutor} 接口</b>：LOOP 节点的行为是编图期动态生成的闭包（捕获了该 LOOP 专属的
 * {@link LoopBody}），不走 {@code executors.get(type)} 路径。本类是无状态循环驱动器，由 {@link FlowGraphFactory}
 * 持有并在 LOOP 节点闭包里调用 {@link #execute(FlowNodeDefinition, OrchestrationContext, LoopBody)}。
 *
 * <p><b>每轮子 ctx 隔离</b>：每轮新建子 {@link OrchestrationContext}，浅灌父 attributes（成员能读 START 入参与上游产物）
 * 叠加约定键 {@code __loopItem}/{@code __loopIndex}/{@code __loopCount}（可用 {@code loop.itemVar} 自定义项名）。
 * 每轮出口产物（子 ctx 的 {@code __output}，无则整份子 attributes 快照）append 到列表，循环结束整体写父 ctx
 * {@code __loopResults}（+ {@code __loopLastResult}；LOOP 配了 outputKey 则同名再写一份供上游变量选取）。
 * 整体写回而非每轮直写——下游拓扑序在 LOOP 后执行，每轮直写会被后续轮覆盖。
 *
 * <p><b>治理</b>：{@code maxIterations} 硬上限（缺省 100，对齐前端 LOOP_DEFAULT_MAX）；{@code breakCondition}
 * 是结构化 CondGroup（非 SpEL），每轮跑完对子 ctx 求值，命中即提前跳出。
 *
 * @author nebula
 */
public class LoopNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(LoopNodeExecutor.class);

    /**
     * 节点类型标识（LOOP）
     */
    public static final String TYPE = "LOOP";

    /**
     * 约定键：当前遍历项（FOREACH 模式）。可被 {@code loop.itemVar} 覆盖为自定义名。
     * 别名转发到 {@link ContextKeys.Loop#ITEM}（唯一权威出处）。
     */
    public static final String LOOP_ITEM_KEY = ContextKeys.Loop.ITEM;

    /**
     * 约定键：当前轮次索引（0 起）。别名转发到 {@link ContextKeys.Loop#INDEX}。
     */
    public static final String LOOP_INDEX_KEY = ContextKeys.Loop.INDEX;

    /**
     * 约定键：总轮数。别名转发到 {@link ContextKeys.Loop#COUNT}。
     */
    public static final String LOOP_COUNT_KEY = ContextKeys.Loop.COUNT;

    /**
     * 约定键：各轮产物收集列表。别名转发到 {@link ContextKeys.Loop#RESULTS}。
     */
    public static final String LOOP_RESULTS_KEY = ContextKeys.Loop.RESULTS;

    /**
     * 约定键：最后一轮产物。别名转发到 {@link ContextKeys.Loop#LAST_RESULT}。
     */
    public static final String LOOP_LAST_RESULT_KEY = ContextKeys.Loop.LAST_RESULT;

    /**
     * 缺省循环硬上限（对齐前端 LOOP_DEFAULT_MAX）
     */
    private static final int DEFAULT_MAX_ITERATIONS = 100;

    private final CondGroupCompiler condGroupCompiler;

    public LoopNodeExecutor(CondGroupCompiler condGroupCompiler) {
        this.condGroupCompiler = condGroupCompiler;
    }

    /**
     * 执行 LOOP 节点：按 FOREACH/COUNT 循环跑子图 body。
     *
     * @param node   LOOP 节点定义（读 nodeConfig.loop）
     * @param ctx    父编排上下文
     * @param body   该 LOOP 的成员子图（编图期已构造并闭包捕获）
     */
    @SuppressWarnings("unchecked")
    public void execute(FlowNodeDefinition node, OrchestrationContext ctx, LoopBody body) {
        Map<String, Object> loop = asMap(node.getNodeConfig().get("loop"));
        String mode = str(loop.get("mode"), "COUNT").toUpperCase();
        int maxIterations = intVal(loop.get("maxIterations"), DEFAULT_MAX_ITERATIONS);
        if (maxIterations <= 0) {
            maxIterations = DEFAULT_MAX_ITERATIONS;
        }
        String itemVar = str(loop.get("itemVar"), LOOP_ITEM_KEY);
        Predicate<OrchestrationContext> breakCond = condGroupCompiler == null ? null
                : condGroupCompiler.compile(loop.get("breakCondition"));

        // 解析遍历源：FOREACH 取列表逐项；COUNT 取次数
        List<Object> items;
        int total;
        boolean foreach = "FOREACH".equals(mode);
        if (foreach) {
            items = resolveItems(loop.get("itemsExpr"), ctx);
            total = Math.min(items.size(), maxIterations);
        } else {
            items = List.of();
            int count = intVal(loop.get("count"), 0);
            total = Math.min(Math.max(count, 0), maxIterations);
        }

        log.info("LOOP 节点[{}] mode={} 计划轮数={}（上限={}）", node.getNodeCode(), mode, total, maxIterations);

        List<Object> results = new ArrayList<>();
        Object lastResult = null;
        for (int i = 0; i < total; i++) {
            OrchestrationContext sub = newSubContext(ctx);
            sub.put(LOOP_INDEX_KEY, i);
            sub.put(LOOP_COUNT_KEY, total);
            if (foreach) {
                Object item = items.get(i);
                sub.put(itemVar, item);
                // itemVar 自定义时同时补写标准键，成员模板/条件可统一用 __loopItem 引用
                if (!LOOP_ITEM_KEY.equals(itemVar)) {
                    sub.put(LOOP_ITEM_KEY, item);
                }
            }

            body.runOnce(sub);

            Object roundResult = collectRoundResult(sub);
            if (roundResult != null) {
                results.add(roundResult);
                lastResult = roundResult;
            }

            if (breakCond != null && breakCond.test(sub)) {
                log.info("LOOP 节点[{}] 第 {} 轮命中 break 条件，提前跳出", node.getNodeCode(), i);
                break;
            }
        }

        // 整体写回父 ctx（每轮直写会被后续轮/下游覆盖，故循环结束一次性写）
        ctx.put(LOOP_RESULTS_KEY, results);
        if (lastResult != null) {
            ctx.put(LOOP_LAST_RESULT_KEY, lastResult);
        }
        // LOOP 配了 outputKey：产物列表同名再写一份，供 useUpstreamVars 下拉选取
        String outputKey = node.getOutputKey();
        if (outputKey != null && !outputKey.isBlank()) {
            ctx.put(outputKey, results);
        }
        log.info("LOOP 节点[{}] 完成，收集产物 {} 项", node.getNodeCode(), results.size());
    }

    /**
     * 新建本轮子上下文：浅灌父 attributes（成员能读 START 入参与上游产物）+ 继承 user/conversation。
     * 子 ctx 隔离——本轮 __loop* 与产物不回污父 ctx，嵌套时内层覆盖同名键也不影响外层。
     */
    private OrchestrationContext newSubContext(OrchestrationContext parent) {
        OrchestrationContext sub = new OrchestrationContext(parent.getUserId(), parent.getConversationId());
        parent.attributes().forEach(sub::put);
        return sub;
    }

    /**
     * 收集一轮产物：优先取子 END 渲染的完整结果 {@code __output}；无 END 时退化为整份子 attributes 快照。
     */
    private Object collectRoundResult(OrchestrationContext sub) {
        Object output = sub.get(EndNodeExecutor.OUTPUT_KEY);
        if (output != null) {
            return output;
        }
        // 无 END 节点的循环体：带回本轮全部产物（浅拷贝，剔除承载性约定键避免噪声）
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>(sub.attributes());
        snapshot.remove(LOOP_INDEX_KEY);
        snapshot.remove(LOOP_COUNT_KEY);
        return snapshot.isEmpty() ? null : snapshot;
    }

    /**
     * 解析 FOREACH 遍历源。前端「遍历列表」下拉选的是上游变量键，故优先按键直取 {@code ctx.get(key)}；
     * 取到 List/数组/Collection 直接用，字符串按逗号分隔，其余非空值降级为单元素列表，缺失/空 → 0 轮（安全）。
     */
    private List<Object> resolveItems(Object itemsExpr, OrchestrationContext ctx) {
        String key = str(itemsExpr, "").trim();
        if (key.isEmpty()) {
            return List.of();
        }
        Object raw = ctx.get(key);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (raw instanceof Object[] arr) {
            return new ArrayList<>(Arrays.asList(arr));
        }
        if (raw instanceof Collection<?> coll) {
            return new ArrayList<>(coll);
        }
        if (raw instanceof CharSequence cs) {
            String s = cs.toString().trim();
            if (s.isEmpty()) {
                return List.of();
            }
            // JSON 数组样式或逗号分隔：按逗号切分（去括号/引号），单值则单元素
            if (s.contains(",")) {
                List<Object> parts = new ArrayList<>();
                for (String p : s.replaceAll("^[\\[\\s]+|[\\]\\s]+$", "").split(",")) {
                    String t = p.trim().replaceAll("^[\"']|[\"']$", "");
                    if (!t.isEmpty()) {
                        parts.add(t);
                    }
                }
                return parts;
            }
            List<Object> single = new ArrayList<>();
            single.add(s);
            return single;
        }
        // 其他单值：作为单元素遍历
        List<Object> single = new ArrayList<>();
        single.add(raw);
        return single;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object v) {
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private String str(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }

    private int intVal(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? def : Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
