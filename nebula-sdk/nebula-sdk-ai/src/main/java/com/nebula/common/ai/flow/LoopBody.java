package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.DagOrchestrator;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.ai.orchestration.OrchestrationGraph;

/**
 * 循环体（LOOP 成员子图）
 * 一个 LOOP 节点的「循环体」= 其 members 编成的一张内存 DAG。{@link FlowGraphFactory} 在编图期为每个
 * LOOP 节点构造本对象并<b>闭包捕获</b>进主图的 LOOP 节点行为里，运行期 {@link LoopNodeExecutor} 每轮调
 * {@link #runOnce(OrchestrationContext)} 跑一遍子图。以「构造期编好、闭包传入」取代全局注册表，避免为传递
 * flowCode/version 而污染上下文。
 *
 * <p>子图 orchestrator 用<b>无 {@code RunStateStore}</b> 的 {@link DagOrchestrator}：子图不落库、不参与续跑
 * （续跑粒度是 LOOP 整体，中途崩溃续跑会整个 LOOP 重跑，不污染 ai_flow_run）。子图与主图同构，故子图内
 * 若再含 LOOP 节点，其行为闭包同样已捕获自己的 LoopBody，嵌套天然成立。
 *
 * @author nebula
 */
public class LoopBody {

    /**
     * 成员子图（members 编成的 DAG，可含 IF/JOIN/嵌套 LOOP）
     */
    private final OrchestrationGraph graph;

    /**
     * 子图编排器（无状态存储，纯内存执行）
     */
    private final DagOrchestrator orchestrator;

    public LoopBody(OrchestrationGraph graph, DagOrchestrator orchestrator) {
        this.graph = graph;
        this.orchestrator = orchestrator;
    }

    /**
     * 跑一轮子图。
     *
     * @param subCtx 本轮子上下文（调用方已灌好父 attributes + __loop* 约定键）
     * @return 执行后的子上下文（同 subCtx，承载本轮产物）
     */
    public OrchestrationContext runOnce(OrchestrationContext subCtx) {
        return orchestrator.run(graph, subCtx);
    }

    /**
     * 成员子图（供测试/诊断读取，如断言成员数）
     *
     * @return 成员子图
     */
    public OrchestrationGraph graph() {
        return graph;
    }
}
