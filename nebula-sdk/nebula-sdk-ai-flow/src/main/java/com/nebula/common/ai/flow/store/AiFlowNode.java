package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI流程节点表
 * 描述流程内一个节点「做什么」。对应 SDK 的 {@code com.nebula.common.ai.flow.FlowNodeDefinition}。
 * JSON 列（stop/options/input_mapping/node_config）以字符串存储，序列化在仓储层处理。
 *
 * @author nebula
 */
@Data
@TableName("ai_flow_node")
public class AiFlowNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节点ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 归属流程编码
     */
    private String flowCode;

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
    private String nodeType;

    /**
     * 状态机语义：ENTRY | NORMAL | TERMINAL（DAG 忽略）
     */
    private String stateType;

    /**
     * 系统提示词模板
     */
    private String systemPrompt;

    /**
     * 用户提示词模板
     */
    private String promptTemplate;

    /**
     * 引用的模型档案编码
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
     * 停止词（JSON数组文本）
     */
    private String stop;

    /**
     * 扩展参数（JSON对象文本）
     */
    private String options;

    /**
     * 输入映射：模板变量名->上下文键（JSON对象文本）
     */
    private String inputMapping;

    /**
     * 产物写回上下文的键
     */
    private String outputKey;

    /**
     * 产物模式：TEXT/JSON
     */
    private String outputMode;

    /**
     * 非PROMPT类型的扩展配置（JSON对象文本）
     */
    private String nodeConfig;

    /**
     * 是否记录该节点轨迹
     */
    private Boolean rememberTrace;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
