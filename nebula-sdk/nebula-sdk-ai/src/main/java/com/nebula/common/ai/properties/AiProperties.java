package com.nebula.common.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI运行时配置属性
 * 当前提供一个OpenAI兼容的Provider配置块，可指向任意遵循 OpenAI /v1/chat/completions
 * 协议的服务（OpenAI、DeepSeek、Moonshot、通义千问兼容模式、vLLM 等）。
 *
 * @author nebula
 */
@Data
@ConfigurationProperties(prefix = "nebula.ai")
public class AiProperties {

    /**
     * 是否启用AI运行时。关闭时不装配任何Provider/AiService，博客等Agent将不可用。
     */
    private boolean enabled = false;

    /**
     * 默认服务提供商标识，当 {@code AiRequest.provider} 未指定时使用，
     * 需与某个Provider的 {@code getProvider()} 对应。
     */
    private String provider = "openai";

    /**
     * OpenAI兼容服务配置
     */
    private OpenAi openai = new OpenAi();

    /**
     * 工具调用（function-calling 闭环）配置
     */
    private ToolCalling toolCalling = new ToolCalling();

    /**
     * 工具调用（function-calling 闭环）配置
     * 控制模型「自主决定调哪个工具并循环执行」的运行边界，与具体Provider无关。
     */
    @Data
    public static class ToolCalling {

        /**
         * 是否启用工具调用闭环。关闭时 {@code chatWithTools} 退化为不下发 tools 的普通对话。
         */
        private boolean enabled = false;

        /**
         * 工具循环最大迭代次数，防止模型无限调用工具；到达上限仍未终止则返回最后一次响应并标记截断。
         */
        private int maxIterations = 5;

        /**
         * 单次工具执行整体超时（毫秒），线程级兜底，避免某个工具卡死拖垮整个循环。
         */
        private int toolTimeoutMs = 10000;
    }

    /**
     * OpenAI兼容服务配置
     */
    @Data
    public static class OpenAi {

        /**
         * 服务提供商标识，对接非OpenAI端点时可改为 deepseek/moonshot 等，
         * 并将外层 {@link AiProperties#provider} 同步为该值。
         */
        private String provider = "openai";

        /**
         * API基础地址，须为OpenAI兼容的根地址（含 /v1），调用时拼接 /chat/completions。
         */
        private String baseUrl = "https://api.openai.com/v1";

        /**
         * API密钥
         */
        private String apiKey;

        /**
         * 默认模型，{@code AiRequest.model} 未指定时使用
         */
        private String model = "gpt-4o-mini";

        /**
         * 采样温度，{@code AiRequest.temperature} 未指定时使用
         */
        private Double temperature = 0.7;

        /**
         * 最大输出token数，{@code AiRequest.maxTokens} 未指定时使用
         */
        private Integer maxTokens = 2048;

        /**
         * Top P采样参数，{@code AiRequest.topP} 未指定时使用；为null则不下发
         */
        private Double topP;

        /**
         * 请求超时（毫秒），覆盖连接获取与响应读取
         */
        private int timeoutMs = 60000;
    }
}
