package com.nebula.common.ai.flow;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型档案
 * 集中描述一组「模型连接 + 默认调用参数」，可被多个流程/节点引用复用：切换端点、密钥或默认参数时
 * 只需改档案本身。所有字段均可为空，空字段在参数合并时透明回退到更低优先级（流程默认 / 全局配置）。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class ModelProfile {

    /**
     * 档案编码，全局唯一
     */
    private String profileCode;

    /**
     * 档案名称
     */
    private String name;

    /**
     * 服务提供商标识
     */
    private String provider;

    /**
     * API基础地址（运行时覆盖全局配置）
     */
    private String baseUrl;

    /**
     * API密钥（运行时覆盖全局配置）
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 采样温度
     */
    private Double temperature;

    /**
     * 最大输出token数
     */
    private Integer maxTokens;

    /**
     * Top P采样参数
     */
    private Double topP;

    /**
     * 请求超时（毫秒）
     */
    private Integer timeoutMs;

    /**
     * 扩展参数，透传厂商私有参数（response_format / frequency_penalty 等）
     */
    private Map<String, Object> options = new LinkedHashMap<>();
}
