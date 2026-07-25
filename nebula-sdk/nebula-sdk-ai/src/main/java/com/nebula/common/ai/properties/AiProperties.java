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
     * 文本向量化（embedding）配置。与聊天模型 {@link #openai} 解耦，独立端点/密钥/模型/维度，
     * 由 {@code nebula-sdk-ai-rag} 的 {@code OpenAiEmbeddingProvider} 消费。
     */
    private Embedding embedding = new Embedding();

    /**
     * Milvus 向量库连接与索引配置。由 {@code nebula-sdk-ai-rag} 的 {@code MilvusAutoConfiguration} 消费。
     */
    private Milvus milvus = new Milvus();

    /**
     * 四个 RAG 检索场景的开关与参数。各场景在向量库未配置/宕机时按 docs 第十一章优雅退化。
     */
    private Rag rag = new Rag();

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
     * 文本向量化（embedding）配置
     * 与聊天模型解耦：向量模型往往来自不同厂商/端点，且维度是强约束。实现复用 {@code aiHttpClient} 连接池，
     * 但端点/密钥/模型各走本段。
     */
    @Data
    public static class Embedding {

        /**
         * 是否装配 {@code OpenAiEmbeddingProvider}。关闭时不产生任何向量能力，四场景全退化。
         */
        private boolean enabled = false;

        /**
         * 提供者编码，对应 {@code ai_knowledge_base.embedding_provider}，全局唯一。
         */
        private String code = "openai";

        /**
         * OpenAI 兼容 embedding 端点根地址（含 /v1），调用时拼接 /embeddings。
         */
        private String baseUrl = "https://api.openai.com/v1";

        /**
         * API 密钥
         */
        private String apiKey;

        /**
         * 向量模型名称
         */
        private String model = "text-embedding-3-small";

        /**
         * 向量维度，必须与建库维度一致，否则检索无意义（读写前会做维度校验）。
         */
        private int dimension = 1536;

        /**
         * 单次请求超时（毫秒）
         */
        private int timeoutMs = 30000;

        /**
         * 批量向量化时单批文本数上限，控制单次请求体大小与厂商入参限制。
         */
        private int batchSize = 16;

        /**
         * 单批失败最大重试次数
         */
        private int maxRetries = 2;
    }

    /**
     * Milvus 向量库连接与索引配置
     * 支持 host+port（standalone/cluster）与 uri+token（Zilliz Cloud）两种连接形态；后者填了 {@code uri} 时优先。
     */
    @Data
    public static class Milvus {

        /**
         * 是否装配 {@code MilvusClientV2} / {@code MilvusVectorStore}。关闭时不产生向量存取能力。
         */
        private boolean enabled = false;

        /**
         * 主机地址（standalone/cluster）
         */
        private String host = "localhost";

        /**
         * 端口（standalone/cluster）
         */
        private int port = 19530;

        /**
         * 连接整串（Zilliz Cloud）。非空时优先于 {@code host}/{@code port}。
         */
        private String uri = "";

        /**
         * 鉴权 token（Zilliz Cloud / 开启鉴权的 Milvus）
         */
        private String token = "";

        /**
         * 数据库名
         */
        private String database = "default";

        /**
         * 是否走 TLS
         */
        private boolean secure = false;

        /**
         * 连接超时（毫秒）
         */
        private long connectTimeoutMs = 10000;

        /**
         * collection 物理名前缀，逻辑名（nebula_kb 等）拼接前缀后为物理名一部分。
         */
        private String collectionPrefix = "nebula_";

        /**
         * 相似度度量，文本 embedding 首选 COSINE。
         */
        private String metric = "COSINE";

        /**
         * 向量索引类型（HNSW / IVF_FLAT）
         */
        private String index = "HNSW";

        /**
         * HNSW 图每层最大出边数
         */
        private int hnswM = 16;

        /**
         * HNSW 建图时的候选队列长度
         */
        private int hnswEfConstruction = 200;

        /**
         * HNSW 检索时的候选队列长度
         */
        private int hnswEf = 64;

        /**
         * 启动时是否自动建库/建索引/建 alias（幂等）。
         */
        private boolean autoCreateCollection = true;
    }

    /**
     * 四个 RAG 检索场景的开关与参数
     * 与向量库连接/embedding 解耦：即便 {@code milvus.enabled=true}，各场景仍可独立按需启用。
     */
    @Data
    public static class Rag {

        /**
         * 场景① 知识库 RAG：knowledge_search 工具注册 + KB 管理面开关。
         */
        private Knowledge knowledge = new Knowledge();

        /**
         * 场景② Flow Copilot few-shot：生成前语义召回相似历史流程作示例。
         */
        private FewShot fewShot = new FewShot();

        /**
         * 场景③ 长期记忆语义召回：db（LIKE，默认）或 vector（语义）。
         */
        private Memory memory = new Memory();

        /**
         * 场景④ 节点/工具语义检索：search_tools 工具 + ToolCatalog 索引器开关。
         */
        private ToolSearch toolSearch = new ToolSearch();

        /**
         * 场景① 参数
         */
        @Data
        public static class Knowledge {

            /**
             * 是否启用知识库 RAG（注册 knowledge_search 工具 + 开放 KB 管理面）。
             */
            private boolean enabled = false;
        }

        /**
         * 场景② 参数
         */
        @Data
        public static class FewShot {

            /**
             * 是否启用 few-shot 召回
             */
            private boolean enabled = false;

            /**
             * 召回条数
             */
            private int topK = 3;

            /**
             * 最小归一化得分阈值
             */
            private double minScore = 0.6;
        }

        /**
         * 场景③ 参数
         */
        @Data
        public static class Memory {

            /**
             * 记忆召回模式：{@code db}（LIKE 关键词，默认）或 {@code vector}（语义召回）。
             */
            private String mode = "db";

            /**
             * 召回条数
             */
            private int topK = 5;

            /**
             * 最小归一化得分阈值
             */
            private double minScore = 0.6;
        }

        /**
         * 场景④ 参数
         */
        @Data
        public static class ToolSearch {

            /**
             * 是否启用工具语义检索（注册 search_tools 工具 + 启用 ToolCatalog 索引器）。
             */
            private boolean enabled = false;
        }
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
