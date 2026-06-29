package com.nebula.common.ai.flow;

import com.nebula.common.ai.orchestration.OrchestrationContext;

import java.util.Map;

/**
 * 工具定义（SPI）
 * 描述一个「流程节点可调用的工具」：自描述元数据（编码/名称/描述/分类/入参schema）+ 调用逻辑（{@link #invoke}）。
 * 每个工具实现本接口并注册为 Bean；{@link ToolRegistry} 在容器内聚合全部实现，{@link ToolNodeExecutor}
 * 在 TOOL 类型节点运行时按 {@link #code()} 选取并调用。
 *
 * <p>代码 = 工具的唯一真相源：启动时由同步器把全部实现的元数据镜像进 {@code ai_tool} 表，供前端流程编辑器
 * 渲染「可选工具列表 + 参数表单」。新增一个工具只需新增一个 {@code ToolDefinition} Bean，引擎与表结构均无需改动。
 *
 * @author nebula
 */
public interface ToolDefinition {

    /**
     * 工具编码，全局唯一，被流程节点 {@code nodeConfig.toolCode} 引用；与 {@code ai_tool.tool_code} 对应
     *
     * @return 工具编码
     */
    String code();

    /**
     * 工具显示名
     *
     * @return 名称，默认取编码
     */
    default String name() {
        return code();
    }

    /**
     * 用途描述（兼作 LLM function description）
     *
     * @return 描述，可空
     */
    default String description() {
        return null;
    }

    /**
     * 分类（http/data/search 等），编辑器分组用
     *
     * @return 分类，可空
     */
    default String category() {
        return null;
    }

    /**
     * 入参 JSON Schema，声明工具所需参数/类型/必填项，编辑器据此渲染参数表单
     *
     * @return 入参 schema，可空
     */
    default Map<String, Object> paramsSchema() {
        return null;
    }

    /**
     * 出参结构描述，给下游节点提示产物形状
     *
     * @return 出参 schema，可空
     */
    default Map<String, Object> resultSchema() {
        return null;
    }

    /**
     * 排序号，编辑器列表排序用
     *
     * @return 排序号，默认 0
     */
    default int sortNo() {
        return 0;
    }

    /**
     * 执行工具：读取已解析的入参，返回产物（由 {@link ToolNodeExecutor} 写回编排上下文）。
     *
     * @param params 入参（已按节点 inputMapping 从上下文解析）
     * @param ctx    编排共享上下文（如需读取超出入参的全局产物）
     * @return 工具产物，可为 null
     */
    Object invoke(Map<String, Object> params, OrchestrationContext ctx);
}
