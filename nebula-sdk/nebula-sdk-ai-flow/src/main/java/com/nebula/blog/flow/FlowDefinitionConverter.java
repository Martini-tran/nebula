package com.nebula.blog.flow;

import com.nebula.blog.entity.AiFlow;
import com.nebula.blog.entity.AiFlowEdge;
import com.nebula.blog.entity.AiFlowNode;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;

/**
 * 流程定义转换器
 * 在持久化实体（AiFlow/AiFlowNode/AiFlowEdge）与 SDK 运行期定义
 * （FlowDefinition/FlowNodeDefinition/FlowEdgeDefinition）之间双向映射。
 * 读路径供 {@link DatabaseFlowDefinitionRepository} 组装定义；写路径供业务保存整图。
 *
 * @author nebula
 */
public final class FlowDefinitionConverter {

    private FlowDefinitionConverter() {
    }

    /* ===================== 实体 -> SDK 定义（读） ===================== */

    /**
     * 流程头实体转 SDK 定义（不含节点/边）
     */
    public static FlowDefinition toDefinition(AiFlow flow) {
        FlowDefinition def = new FlowDefinition();
        def.setFlowCode(flow.getFlowCode());
        def.setName(flow.getName());
        def.setDescription(flow.getDescription());
        def.setVersion(flow.getVersion() == null ? 1 : flow.getVersion());
        def.setDefaultProfileCode(flow.getDefaultProfileCode());
        return def;
    }

    /**
     * 节点实体转 SDK 节点定义
     */
    public static FlowNodeDefinition toNodeDefinition(AiFlowNode entity) {
        FlowNodeDefinition node = new FlowNodeDefinition();
        node.setNodeCode(entity.getNodeCode());
        node.setName(entity.getName());
        node.setNodeType(entity.getNodeType() == null ? "PROMPT" : entity.getNodeType());
        node.setSystemPrompt(entity.getSystemPrompt());
        node.setPromptTemplate(entity.getPromptTemplate());
        node.setProfileCode(entity.getProfileCode());
        node.setProvider(entity.getProvider());
        node.setModel(entity.getModel());
        node.setBaseUrl(entity.getBaseUrl());
        node.setApiKey(entity.getApiKey());
        node.setTemperature(entity.getTemperature());
        node.setMaxTokens(entity.getMaxTokens());
        node.setTopP(entity.getTopP());
        node.setTimeoutMs(entity.getTimeoutMs());
        node.setStop(FlowJsonCodec.readStringList(entity.getStop()));
        node.setOptions(FlowJsonCodec.readObjectMap(entity.getOptions()));
        node.setInputMapping(FlowJsonCodec.readStringMap(entity.getInputMapping()));
        node.setOutputKey(entity.getOutputKey());
        node.setOutputMode(entity.getOutputMode() == null ? "TEXT" : entity.getOutputMode());
        node.setNodeConfig(FlowJsonCodec.readObjectMap(entity.getNodeConfig()));
        node.setRememberTrace(Boolean.TRUE.equals(entity.getRememberTrace()));
        node.setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo());
        return node;
    }

    /**
     * 边实体转 SDK 边定义
     */
    public static FlowEdgeDefinition toEdgeDefinition(AiFlowEdge entity) {
        FlowEdgeDefinition edge = new FlowEdgeDefinition();
        edge.setFromNode(entity.getFromNode());
        edge.setToNode(entity.getToNode());
        edge.setConditionExpr(entity.getConditionExpr());
        edge.setSortNo(entity.getSortNo() == null ? 0 : entity.getSortNo());
        return edge;
    }

    /* ===================== SDK 定义 -> 实体（写） ===================== */

    /**
     * SDK 节点定义转实体（归属指定流程编码）
     */
    public static AiFlowNode toNodeEntity(String flowCode, FlowNodeDefinition node) {
        AiFlowNode entity = new AiFlowNode();
        entity.setFlowCode(flowCode);
        entity.setNodeCode(node.getNodeCode());
        entity.setName(node.getName());
        entity.setNodeType(node.getNodeType() == null ? "PROMPT" : node.getNodeType());
        entity.setSystemPrompt(node.getSystemPrompt());
        entity.setPromptTemplate(node.getPromptTemplate());
        entity.setProfileCode(node.getProfileCode());
        entity.setProvider(node.getProvider());
        entity.setModel(node.getModel());
        entity.setBaseUrl(node.getBaseUrl());
        entity.setApiKey(node.getApiKey());
        entity.setTemperature(node.getTemperature());
        entity.setMaxTokens(node.getMaxTokens());
        entity.setTopP(node.getTopP());
        entity.setTimeoutMs(node.getTimeoutMs());
        entity.setStop(FlowJsonCodec.write(node.getStop()));
        entity.setOptions(FlowJsonCodec.write(node.getOptions()));
        entity.setInputMapping(FlowJsonCodec.write(node.getInputMapping()));
        entity.setOutputKey(node.getOutputKey());
        entity.setOutputMode(node.getOutputMode() == null ? "TEXT" : node.getOutputMode());
        entity.setNodeConfig(FlowJsonCodec.write(node.getNodeConfig()));
        entity.setRememberTrace(node.isRememberTrace());
        entity.setSortNo(node.getSortNo());
        return entity;
    }

    /**
     * SDK 边定义转实体（归属指定流程编码）
     */
    public static AiFlowEdge toEdgeEntity(String flowCode, FlowEdgeDefinition edge) {
        AiFlowEdge entity = new AiFlowEdge();
        entity.setFlowCode(flowCode);
        entity.setFromNode(edge.getFromNode());
        entity.setToNode(edge.getToNode());
        entity.setConditionExpr(edge.getConditionExpr());
        entity.setSortNo(edge.getSortNo());
        return entity;
    }
}
