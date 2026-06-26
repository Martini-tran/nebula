package com.nebula.common.ai.flow;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程节点定义
 * 一个节点描述「做什么」：节点类型决定由哪个 {@link FlowNodeExecutor} 执行。对最常见的 PROMPT 类型，
 * 节点持有提示词模板（{@code #{var}} 占位）与模型参数覆盖，运行时渲染模板并调用模型，产物写回上下文。
 *
 * <p>模型参数（{@code provider/model/...}）非空时覆盖所引用档案的同名值；为空则回退到
 * 档案 → 流程默认档案 → 全局配置。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class FlowNodeDefinition {

    /**
     * 节点编码，流程内唯一（= 编排图节点ID）
     */
    private String nodeCode;

    /**
     * 节点名称
     */
    private String name;

    /**
     * 节点类型，决定执行器；默认 PROMPT
     */
    private String nodeType = "PROMPT";

    /**
     * 系统提示词模板（{@code #{var}} 占位，可空）
     */
    private String systemPrompt;

    /**
     * 用户提示词模板（{@code #{var}} 占位）
     */
    private String promptTemplate;

    /**
     * 引用的模型档案编码，空则用流程默认档案
     */
    private String profileCode;

    /**
     * 服务提供商（覆盖档案）
     */
    private String provider;

    /**
     * 模型名称（覆盖档案）
     */
    private String model;

    /**
     * API基础地址（覆盖档案）
     */
    private String baseUrl;

    /**
     * API密钥（覆盖档案）
     */
    private String apiKey;

    /**
     * 采样温度（覆盖档案）
     */
    private Double temperature;

    /**
     * 最大输出token数（覆盖档案）
     */
    private Integer maxTokens;

    /**
     * Top P采样参数（覆盖档案）
     */
    private Double topP;

    /**
     * 请求超时毫秒（覆盖档案）
     */
    private Integer timeoutMs;

    /**
     * 停止词（覆盖档案）
     */
    private List<String> stop = new ArrayList<>();

    /**
     * 扩展参数，逐键合并覆盖档案的同名键
     */
    private Map<String, Object> options = new LinkedHashMap<>();

    /**
     * 输入映射：模板变量名 -> 上下文键。为空则模板变量直接取上下文同名键。
     */
    private Map<String, String> inputMapping = new LinkedHashMap<>();

    /**
     * 产物写回上下文的键
     */
    private String outputKey;

    /**
     * 产物模式：TEXT 整段写入 outputKey；JSON 解析模型输出后逐键展开到上下文
     */
    private String outputMode = "TEXT";

    /**
     * 非 PROMPT 类型的扩展配置（如 HTTP 的 url、SCRIPT 的脚本）
     */
    private Map<String, Object> nodeConfig = new LinkedHashMap<>();

    /**
     * 是否记录该节点轨迹到编排上下文
     */
    private boolean rememberTrace;

    /**
     * 排序号
     */
    private int sortNo;
}
