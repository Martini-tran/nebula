package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.DagOrchestrator;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationException;
import com.nebula.common.ai.orchestration.OrchestrationGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LOOP 内嵌子图循环单测：构图剔除成员、FOREACH/COUNT 遍历、maxIterations 截断、break、嵌套。
 * 用 {@link CountingNodeExecutor} 记录成员跨轮执行次数，用无状态 {@link DagOrchestrator} 跑主图。
 *
 * @author nebula
 */
class LoopNodeExecutorTest {

    private final CountingNodeExecutor executor = new CountingNodeExecutor();

    private final LoopNodeExecutor loopExecutor = new LoopNodeExecutor(new CondGroupCompiler());

    private final FlowGraphFactory factory =
            new FlowGraphFactory(List.of(executor), new ConditionCompiler(), loopExecutor);

    private final DagOrchestrator orchestrator = new DagOrchestrator();

    private FlowNodeDefinition node(String code, String type) {
        return new FlowNodeDefinition().setNodeCode(code).setNodeType(type).setPromptTemplate("x");
    }

    /** START → LOOP(members=[gen]) → END 的骨架，loopConfig 由调用方注入 */
    private FlowDefinition foreachFlow(Map<String, Object> loopConfig, List<String> members) {
        FlowDefinition def = new FlowDefinition().setFlowCode("blog");
        def.getNodes().add(node("start", "PROMPT").setSortNo(0));
        FlowNodeDefinition loop = node("loop", "LOOP").setSortNo(1);
        loop.getNodeConfig().put("members", members);
        loop.getNodeConfig().put("loop", loopConfig);
        def.getNodes().add(loop);
        def.getNodes().add(node("gen", "PROMPT").setSortNo(2));
        def.getNodes().add(node("end", "PROMPT").setSortNo(3));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("start").setToNode("gen").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("gen").setToNode("end").setSortNo(1));
        return def;
    }

    @Test
    void 成员被剔除出主图_只在子图循环执行() {
        FlowDefinition def = foreachFlow(
                Map.of("mode", "FOREACH", "itemsExpr", "catalog"), List.of("gen"));
        OrchestrationGraph graph = factory.build(def);

        // 成员 gen 不在主图；主图 = start / loop / end
        assertFalse(graph.nodeIds().contains("gen"));
        assertTrue(graph.nodeIds().contains("loop"));
        assertEquals(List.of("start"), graph.roots());

        OrchestrationContext ctx = new OrchestrationContext();
        ctx.put("catalog", List.of("引言", "正文", "结语"));
        orchestrator.run(graph, ctx);

        // gen 执行 3 次（每项一次），start/end 各 1 次
        assertEquals(3, executor.count("gen"));
        assertEquals(1, executor.count("start"));
        assertEquals(1, executor.count("end"));
        // __loopResults 收集 3 项
        assertEquals(3, ((List<?>) ctx.get(LoopNodeExecutor.LOOP_RESULTS_KEY)).size());
        // 遍历项序列正确
        assertEquals(List.of("引言", "正文", "结语"), executor.itemsSeen.get("gen"));
    }

    @Test
    void FOREACH空列表零轮() {
        FlowDefinition def = foreachFlow(
                Map.of("mode", "FOREACH", "itemsExpr", "missing"), List.of("gen"));
        OrchestrationContext ctx = new OrchestrationContext();
        orchestrator.run(factory.build(def), ctx);
        assertEquals(0, executor.count("gen"));
        assertEquals(0, ((List<?>) ctx.get(LoopNodeExecutor.LOOP_RESULTS_KEY)).size());
    }

    @Test
    void COUNT按次数循环() {
        FlowDefinition def = foreachFlow(
                Map.of("mode", "COUNT", "count", 4), List.of("gen"));
        OrchestrationContext ctx = new OrchestrationContext();
        orchestrator.run(factory.build(def), ctx);
        assertEquals(4, executor.count("gen"));
    }

    @Test
    void maxIterations截断() {
        FlowDefinition def = foreachFlow(
                Map.of("mode", "COUNT", "count", 10, "maxIterations", 3), List.of("gen"));
        OrchestrationContext ctx = new OrchestrationContext();
        orchestrator.run(factory.build(def), ctx);
        assertEquals(3, executor.count("gen"));
    }

    @Test
    void break命中提前跳出() {
        // 遍历 5 项，当 __loopItem eq "stop" 时跳出（在第 3 项，索引 2）
        Map<String, Object> breakCond = Map.of("logic", "AND", "clauses",
                List.of(Map.of("left", LoopNodeExecutor.LOOP_ITEM_KEY, "op", "eq", "right", "stop")));
        FlowDefinition def = foreachFlow(
                Map.of("mode", "FOREACH", "itemsExpr", "catalog", "breakCondition", breakCond),
                List.of("gen"));
        OrchestrationContext ctx = new OrchestrationContext();
        ctx.put("catalog", new ArrayList<>(List.of("a", "b", "stop", "d", "e")));
        orchestrator.run(factory.build(def), ctx);
        // 跑到 stop（含）就跳出：a,b,stop → 3 次
        assertEquals(3, executor.count("gen"));
    }

    @Test
    void 循环体含IF分支_条件对含loopItem的子ctx求值() {
        // members=[gen, hot]；gen→hot 边条件 __loopItem == '热' 才走 hot
        FlowDefinition def = new FlowDefinition().setFlowCode("blog2");
        def.getNodes().add(node("start", "PROMPT").setSortNo(0));
        FlowNodeDefinition loop = node("loop", "LOOP").setSortNo(1);
        loop.getNodeConfig().put("members", List.of("gen", "hot"));
        loop.getNodeConfig().put("loop", Map.of("mode", "FOREACH", "itemsExpr", "catalog"));
        def.getNodes().add(loop);
        def.getNodes().add(node("gen", "PROMPT").setSortNo(2));
        def.getNodes().add(node("hot", "PROMPT").setSortNo(3));
        def.getNodes().add(node("end", "PROMPT").setSortNo(4));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("start").setToNode("gen").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("gen").setToNode("hot")
                .setConditionExpr("get('__loopItem') == '热'").setSortNo(1));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("hot").setToNode("end").setSortNo(2));

        OrchestrationContext ctx = new OrchestrationContext();
        ctx.put("catalog", List.of("热", "冷", "热"));
        orchestrator.run(factory.build(def), ctx);
        assertEquals(3, executor.count("gen"));  // 每项都跑 gen
        assertEquals(2, executor.count("hot"));  // 仅 "热" 两项走 hot
    }

    @Test
    void 嵌套LOOP_外层M乘内层N() {
        // 外层 LOOP(outer) COUNT=2，成员=[inner, mid]；内层 LOOP(inner) COUNT=3，成员=[leaf]
        FlowDefinition def = new FlowDefinition().setFlowCode("nested");
        def.getNodes().add(node("start", "PROMPT").setSortNo(0));

        FlowNodeDefinition outer = node("outer", "LOOP").setSortNo(1);
        outer.getNodeConfig().put("members", List.of("inner", "leaf"));
        outer.getNodeConfig().put("loop", Map.of("mode", "COUNT", "count", 2));
        def.getNodes().add(outer);

        FlowNodeDefinition inner = node("inner", "LOOP").setSortNo(2);
        inner.getNodeConfig().put("members", List.of("leaf"));
        inner.getNodeConfig().put("loop", Map.of("mode", "COUNT", "count", 3));
        def.getNodes().add(inner);

        def.getNodes().add(node("leaf", "PROMPT").setSortNo(3));
        def.getNodes().add(node("end", "PROMPT").setSortNo(4));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("start").setToNode("outer").setSortNo(0));
        def.getEdges().add(new FlowEdgeDefinition().setFromNode("outer").setToNode("end").setSortNo(1));

        OrchestrationGraph graph = factory.build(def);
        // 主图只有 start/outer/end；inner、leaf 都被剔除
        assertFalse(graph.nodeIds().contains("inner"));
        assertFalse(graph.nodeIds().contains("leaf"));
        assertTrue(graph.nodeIds().contains("outer"));

        orchestrator.run(graph, new OrchestrationContext());
        // leaf 执行 外2 × 内3 = 6 次
        assertEquals(6, executor.count("leaf"));
    }

    @Test
    void 未装配LoopExecutor时含LOOP流程构图报错() {
        FlowGraphFactory noLoop = new FlowGraphFactory(List.of(executor), new ConditionCompiler());
        FlowDefinition def = foreachFlow(Map.of("mode", "COUNT", "count", 1), List.of("gen"));
        OrchestrationException ex = assertThrows(OrchestrationException.class, () -> noLoop.build(def));
        assertTrue(ex.getMessage().contains("LOOP"));
    }
}
