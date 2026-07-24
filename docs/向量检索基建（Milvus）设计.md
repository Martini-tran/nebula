# nebula 向量检索基建（Milvus）设计 v1

> 本文档回答一个具体问题：
> **"想让整个 AI 平台具备语义检索能力（首先服务于核心的对话式流程生成助手 Flow Copilot），引入 Milvus 向量库，应该建一个什么样的后端？"**
>
> 核心结论先说：**新建一个独立模块 `nebula-sdk-ai-rag` 承载 Milvus 运行时，SPI 抽象仍留在纯内核 `nebula-sdk-ai` 的 `com.nebula.common.ai.rag`**。用一个 `OpenAiEmbeddingProvider`（走独立配置 `nebula.ai.embedding.*`，复用现成 `aiHttpClient` 调 `/embeddings`）产生向量，用一个 `MilvusVectorStore`（`MilvusClientV2`）存取向量，向上服务**四个检索场景**：① 知识库 RAG ② Flow Copilot few-shot 检索 ③ 长期记忆语义召回 ④ 节点/工具语义检索。
>
> 四个场景全部挂在**既有的三个 seam** 上 —— `ToolRegistry`（工具）、`LongTermMemory`（记忆）、Flow Copilot 的 driver（前置召回）——**不触碰任何编排内核**（`FlowEngine`/`DagOrchestrator`/`StateMachineOrchestrator`/`AgentEngine` 零改动）。
>
> 本设计承接 [对话式流程生成与Agent派生设计.md](./对话式流程生成与Agent派生设计.md) 第八章的 RAG/向量**占位 SPI**，把它落地为 Milvus 实现并从"仅知识库"扩展到四场景。

---

## 〇、先厘清：这套基建是什么，不是什么

| 维度 | 是 | 不是 |
|---|---|---|
| 定位 | 一层**统一的向量检索基建**（embed + 存取 + 四类召回） | 不是给某一个场景写死的一次性 RAG |
| 归属 | Milvus 运行时在**独立新模块** `nebula-sdk-ai-rag` | 不塞进被所有 AI 服务依赖的纯内核 `nebula-sdk-ai` |
| 接入 | 全部挂 `ToolDefinition`/`LongTermMemory`/driver 三个既有 seam | 不新增节点类型、不改 `FlowNodeExecutor`、不改编排内核 |
| embedding | **独立配置** `nebula.ai.embedding.*`（与聊天模型解耦） | 不复用 `nebula.ai.openai.*` 的模型/密钥 |
| 可用性 | Milvus 未配置或宕机时**四场景各自优雅退化**，主链路不阻断 | 不让向量库成为 Flow Copilot / Agent 执行的强依赖 |
| 交付 | 本期只出**设计文档**；实现分四批次 | 本期不写实现代码 |

> **关键认知**：底层 seam（`EmbeddingProvider`/`VectorStore` SPI、`ToolRegistry` 自动聚合、`LongTermMemory` 可选装配）几乎是为此预留的。**缺的只有"Milvus 实现 + embedding 实现 + 四个消费点接线"**。所以本设计的重心不是造抽象，而是**把已有 seam 用 Milvus 填满，并解决"重依赖隔离 / 维度漂移 / 不可用降级"三个真正的工程难点**。

---

## 一、背景与现状

### 1.1 核心 agent = Flow Copilot（不是通用聊天）
真正的"AI 生成流程 agent"是 `nebula-service-manager` 的 `com.nebula.manager.ai.copilot.FlowCopilotService` —— 一个手写的**混合流式工具循环**，通过 `generate_flow`/`derive_agent`/`list_node_types`/`list_tools`/`list_model_profiles` 五个 `ToolDefinition` 把自然语言需求转成 DAG 流程并落 `ai_flow` 三表。它当前**无任何检索能力**，可用节点/工具/示例全靠 `CopilotSystemPrompt` 内联。（用户初始打开的 `AiChatAdminController` 只是通用流式对话透传，与流程生成无关。）

### 1.2 向量能力现状 = 空白 + 占位 SPI
`com.nebula.common.ai.rag` 下仅 3 个占位接口，**无实现、无依赖、无 DDL、无任何引用**：

```java
interface EmbeddingProvider { String code(); List<float[]> embed(List<String> texts); int dimension(); }
interface VectorStore { void upsert(List<VectorChunk>, List<float[]>); List<VectorChunk> search(String kbCode, float[] queryVector, int topK); void deleteByDoc(String kbCode, String docId); }
record VectorChunk(String kbCode, String docId, int chunkIndex, String content, double score, Map<String,Object> metadata) {}
```

### 1.3 记忆召回现状 = 关键词 LIKE
`LongTermMemory` 唯一实现 `DatabaseLongTermMemory`（在 `nebula-service-blog`）的 `search()` 走 `LIKE content + orderByDesc(createTime) + limit topK`；`MemoryRecord.score` / `MemoryQuery.minScore` 字段已预埋但从不填充。`ai_memory` 表**甚至没有正式 DDL**（不在 `script/nebula.sql`）。

### 1.4 四个目标场景（用户已确认全要）
| # | 场景 | 收益 |
|---|---|---|
| ① | **知识库 RAG** | 用户上传文档→切块→向量化，`knowledge_search` 工具供流程 TOOL/AGENT_REACT 节点与 Copilot 检索 |
| ② | **Flow Copilot few-shot** | 生成新流程前语义召回相似历史流程作示例注入提示词，直接提升核心 agent 生成质量 |
| ③ | **长期记忆语义召回** | 把 LIKE 换成向量语义召回，填 `score`/`minScore`，Agent 记忆更准 |
| ④ | **节点/工具语义检索** | 工具/节点多起来后，让 Copilot 语义检索候选工具，而非全量塞进提示词 |

### 1.5 非目标（本期不做）
MCP 运行时客户端；检索重排（rerank/cross-encoder）；多模态 embedding；向量库跨机房容灾。这些留待后续，且都不影响本设计的 seam。

---

## 二、硬约束（决定方案走向，均已核验源码）

### 约束 1：`milvus-sdk-java` 是重依赖 → 必须隔离进独立模块
`io.milvus:milvus-sdk-java` 传递引入 gRPC（netty-shaded）/ protobuf 等重依赖。而 `nebula-sdk-ai` 是**纯内核**（仅 `spring-context`/`spring-boot-autoconfigure`/`httpclient5`/`jackson-databind`/`lombok`，无 mybatis），被 `nebula-service-blog` 等**所有** AI 服务依赖。

> **结论**：**新建 `nebula-sdk-ai-rag`** 承载 Milvus 运行时 + 元数据表；SPI 抽象继续留在 `nebula-sdk-ai` 的 `rag` 包。`nebula-sdk-ai-flow`（流程落库/工具同步）职责正交，也不塞。依赖方向：`nebula-sdk-ai-rag → nebula-sdk-ai`，内核反向不依赖。

### 约束 2：embedding 与聊天模型解耦 → 独立配置段
聊天模型与向量模型往往来自不同厂商/端点（如聊天走 A、embedding 走 B），且 embedding 有维度这一强约束。

> **结论**：**新增 `nebula.ai.embedding.*` 独立配置段**（endpoint/apiKey/model/dimension…），不复用 `nebula.ai.openai.*`。实现 `OpenAiEmbeddingProvider` 复用 `AiAutoConfiguration` 的 `aiHttpClient` Bean（HTTP client 无状态可共享），但配置各走各的。

### 约束 3：向量库不可用不得阻断主链路（可用性铁律）
Flow Copilot 生成流程、Agent 执行是核心链路；向量检索是**增强**，不是必需。

> **结论**：四场景在"未配置"与"Milvus 宕机"两态下都有明确退化路径（详见第十一章）。**few-shot 与记忆检索失败一律静默降级，绝不让异常逸出到 Copilot / Agent 主循环。**

### 约束 4：`VectorStore` 现签名只够单一知识库 → 向后兼容扩展
现签名以 `kbCode` 为唯一命名空间，只契合场景①；场景②③④需要不同 collection + 灵活标量过滤。

> **结论**：**保留原三方法作知识库门面**（向后兼容第八章设计），**新增泛化方法**（collection + `VectorQuery` + `filterExpr`）。原 `search(kbCode,…)` 内部委托到泛化方法。

---

## 三、总体架构

```text
                         ┌──────────────────────────────────────────────────────┐
   四类消费者(seam)        │  ① knowledge_search(ToolDefinition)                    │
                         │  ② FlowCopilot 前置召回(driver, 非工具)                 │
                         │  ③ VectorLongTermMemory(implements LongTermMemory)     │
                         │  ④ search_tools(ToolDefinition) + ToolCatalog 索引器    │
                         └───────────────┬──────────────────────────────────────┘
                                         │ 仅依赖 SPI（不认 Milvus）
   ───────────────────────  SPI（在 nebula-sdk-ai / rag 包）─────────────────────
                         ┌───────────────┴───────────────┐
                         │  EmbeddingProvider            │  VectorStore
                         └───────────────┬───────────────┘
   ───────────────────  实现（在 nebula-sdk-ai-rag，独立模块）────────────────────
                ┌────────────────────────┴─────────────────────────┐
                │  OpenAiEmbeddingProvider   │   MilvusVectorStore   │
                │  （HTTP /embeddings）       │   （MilvusClientV2）   │
                └────────────┬───────────────┴───────────┬──────────┘
                             │ nebula.ai.embedding.*      │ nebula.ai.milvus.*
                       ┌─────┴──────┐              ┌──────┴───────────────────────┐
                       │ 向量模型端点 │              │ Milvus: nebula_kb / _flow_example
                       └────────────┘              │        / _memory / _tool_catalog │
                                                   └──────────────────────────────────┘
   元数据(MySQL)：ai_knowledge_base / ai_knowledge_document / ai_knowledge_chunk / ai_memory
```

**要点**：消费者只依赖 SPI，不认 Milvus；换 pgvector/Qdrant 只换 `nebula-sdk-ai-rag` 实现，上层零改动。编排内核（`FlowEngine`/`DagOrchestrator`/`StateMachineOrchestrator`/`AgentEngine`）完全不参与。

---

## 四、模块归属与依赖

| 模块 | 新增内容 | 依赖 |
|---|---|---|
| `nebula-sdk-ai`（内核，已存在） | 仅 **SPI 扩展**（`VectorStore` 新方法 + `VectorRecord`/`VectorMatch`/`VectorQuery` 值对象）+ `AiProperties` 三个嵌套配置段 | 不变 |
| **`nebula-sdk-ai-rag`（新建）** | `OpenAiEmbeddingProvider`、`MilvusVectorStore`、`MilvusClientV2` 装配、`CollectionInitializer`、`VectorLongTermMemory`、知识库切块/导入服务、元数据实体+Mapper、自动配置 | `nebula-sdk-ai` + `nebula-starter-mybatis` + `io.milvus:milvus-sdk-java` |
| `nebula-bom` | 登记 `nebula-sdk-ai-rag` 内部版本 | — |
| `nebula-parent` | `milvus-sdk-java` 版本 property + `dependencyManagement` | — |
| `nebula-service-manager` | 接入 `nebula-sdk-ai-rag`；`KnowledgeBaseAdmin*` REST、`SearchToolsToolDefinition`、`KnowledgeSearchToolDefinition`、Copilot 前置召回改造 | +`nebula-sdk-ai-rag` |
| `nebula-service-blog` | 可选接入（若 blog Agent 也要向量记忆）；`ai_memory` 实体/Mapper 提升为共享后 blog 改引用 | 可选 +`nebula-sdk-ai-rag` |

> `nebula-sdk-ai-rag` 用 `@ConditionalOnProperty` 门控：不配 Milvus 时该模块所有 Bean 不装配，接入它的服务零副作用。

---

## 五、Milvus 接入

### 5.1 SDK 选型
- 坐标：`io.milvus:milvus-sdk-java`，采用**新式 `MilvusClientV2` API**（`io.milvus.v2.client.MilvusClientV2`），非老的 `MilvusServiceClient`。
- 版本：建议 **2.6.x 稳定线（如 `2.6.13`）**；**须与部署的 Milvus server 大版本对齐**（server 2.5.x 则用 2.5.x）。落地时以 Maven Central 当时的稳定版为准。
- 版本 property + `<dependencyManagement>` 声明在 `nebula-parent/pom.xml`（与现有 spring-boot 4.0.6 / mybatis-plus 3.5.15 同处）；运行时 `<dependency>` **只加在 `nebula-sdk-ai-rag/pom.xml`**。不引 `milvus-sdk-java-bulkwriter`。

### 5.2 连接与装配
- 连接配置 `nebula.ai.milvus.*`（见第十章）：`host`/`port` 或 `uri`（Zilliz 整串）+ `token`/`database`/`secure`/`connect-timeout-ms`。
- 装配类 `MilvusAutoConfiguration`（`@ConditionalOnProperty(nebula.ai.milvus.enabled=true)`）：
  - `MilvusClientV2` Bean（`ConnectConfig` 由属性构造，`@ConditionalOnMissingBean`）
  - `MilvusVectorStore` Bean（`@ConditionalOnBean(MilvusClientV2)`）
  - `CollectionInitializer`（`ApplicationRunner`）：启动时按 `auto-create-collection` 建库/建 HNSW 索引/建 alias，幂等。
- 经 `nebula-sdk-ai-rag/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册（与 sdk-ai 现有 4 条自动配置同一机制）。

### 5.3 部署形态建议
| 场景 | 形态 | 连接 |
|---|---|---|
| 开发 / 中小规模 | Milvus **standalone**（docker-compose，含 etcd + minio） | host+port |
| 大规模 / 高可用 | Milvus **cluster**（k8s） | host+port（LB） |
| 托管免运维 | **Zilliz Cloud** | uri + token |

---

## 六、Collection / Schema 设计

**决策：每场景各一个 collection（共 4 个），共享同一 embedding 模型/维度。** 理由：四场景标量字段异构、写入生命周期不同、可独立重建/调索引/清空，互不影响；共享维度让同一 `EmbeddingProvider` 通吃。

### 6.1 公共字段（4 个 collection 都有）
| 字段 | 类型 | 说明 |
|---|---|---|
| `pk` | VarChar（主键） | 业务**确定性键**，保证 upsert 幂等（Milvus 主键去重靠它） |
| `vector` | FloatVector(dim=embedding.dimension) | 向量列 |
| `scene` | VarChar | 冗余场景标识，便于运维筛查 |
| `content` | VarChar(≤8192) | 正文副本，检索直返省回查 MySQL |
| `create_ts` | Int64 | 写入时间戳（毫秒），支持时间过滤 |

### 6.2 各 collection 专有标量 + partitionKey
| collection | partitionKey（隔离） | 其他标量 | pk 构造 |
|---|---|---|---|
| `nebula_kb` | `kb_code` | `doc_id` / `chunk_index` | `kb_code:doc_id:chunk_index` |
| `nebula_flow_example` | `owner` | `flow_code` / `node_types` | `flow_code` |
| `nebula_memory` | `agent_code` | `user_id` / `conversation_id` / `mem_type` | `ai_memory.id` |
| `nebula_tool_catalog` | `item_type`(TOOL\|NODE_TYPE) | `code` | `item_type:code` |

> partitionKey 让 Milvus 按租户物理分区，检索时按 `kb_code`/`agent_code` 过滤走分区裁剪，多租户隔离 + 提速。

### 6.3 索引与度量
- **索引 HNSW**：`M=16`、`efConstruction=200`、检索 `ef=64`（默认，可配）。备选 `IVF_FLAT`（更大规模/内存敏感）。
- **度量 COSINE**（文本 embedding 首选）。检索返回的余弦相似度统一归一化 `(cos+1)/2 → [0,1]` 再回填 score。

### 6.4 维度 / 模型迁移策略
换 embedding 模型（维度变化）是硬伤，预置方案：
- **物理名嵌维度 + 版本**：如 `nebula_kb__d1536__v1`；**逻辑名走 Milvus alias**（`nebula_kb → nebula_kb__d1536__v1`）。
- 重建：新建 `__v2` collection → 全量 backfill → **alias 原子切换** → 删旧。上层始终用逻辑名，切换无感。
- `ai_knowledge_base` 表落 `embedding_model`/`dimension`/`metric`；**读写前维度校验**，`EmbeddingProvider.dimension()` 与建库维度不符则拒绝（防脏写）。

---

## 七、SPI 扩展（向后兼容）

在 `com.nebula.common.ai.rag` 增加值对象与泛化方法，**保留第八章原三方法**：

```java
// 新增值对象（record）
record VectorRecord(String pk, String content, Map<String,Object> scalars, Map<String,Object> metadata) {}
record VectorMatch(String pk, String content, double score, Map<String,Object> scalars, Map<String,Object> metadata) {}
record VectorQuery(String collection, String partition, float[] vector, int topK, String filterExpr, double minScore) {}

interface VectorStore {
    // —— 原知识库门面（保留，向后兼容 docs 第八章）——
    void upsert(List<VectorChunk> chunks, List<float[]> embeddings);           // 委托 → collection=nebula_kb
    List<VectorChunk> search(String kbCode, float[] queryVector, int topK);    // 委托 → partition=kbCode
    void deleteByDoc(String kbCode, String docId);                            // 委托 → delete by filterExpr

    // —— 新增泛化方法（场景②③④用）——
    void upsert(String collection, List<VectorRecord> records);               // records 自带 vector? 见下
    List<VectorMatch> search(VectorQuery query);
    void delete(String collection, String filterExpr);
}
```

> 说明：为让 `upsert(collection, records)` 也能承载向量，`VectorRecord` 增加 `float[] vector` 字段（上面省略；或保留"records + embeddings 并列"签名与原方法一致，二选一，实现时定）。**score 归一化**：`MilvusVectorStore` 把 COSINE 距离统一转 `[0,1]` 填 `VectorMatch.score` / `VectorChunk.score`；`VectorQuery.minScore` 按归一化值过滤。

---

## 八、EmbeddingProvider（独立配置）

- 独立配置 `nebula.ai.embedding.*`（与 `nebula.ai.openai.*` 解耦，见第十章）。
- 实现 `OpenAiEmbeddingProvider implements EmbeddingProvider`（放 `nebula-sdk-ai-rag`）：
  - `code()` = 配置的 `code`（默认 `openai`），对应 `ai_knowledge_base.embedding_provider`。
  - `embed(texts)`：**复用 `aiHttpClient`（CloseableHttpClient）Bean**，POST OpenAI 兼容 `{base-url}/embeddings`，body `{model, input:[...], dimensions?}`；解析 `data[].embedding` **按 `index` 对齐**回 `List<float[]>`；按 `batch-size` 分批、`max-retries` 重试、超时 `timeout-ms`。
  - `dimension()` = 配置的 `dimension`（与建库维度必须一致）。
- 装配 `@ConditionalOnProperty(nebula.ai.embedding.enabled=true)`。

> 复用 `aiHttpClient` 的理由：它是无状态连接池，聊天与 embedding 共享连接池即可；密钥/端点/模型各自从 `nebula.ai.embedding.*` 取，互不干扰。

---

## 九、四场景接入设计

### 9.1 场景① 知识库 RAG
- **元数据表**（照 `script/nebula.sql` 风格：`utf8mb4_0900_ai_ci` / `bigint AUTO_INCREMENT id` / `xxx_code varchar(64)` uk / `create_time`·`update_time` / json 列 / `tinyint status`）：
  - `ai_knowledge_base(id, kb_code uk, name, description, embedding_provider, embedding_model, dimension, metric, status, create_time, update_time)`
  - `ai_knowledge_document(id, kb_code, doc_id uk, title, source_type, source_uri, char_count, chunk_count, status, create_time, update_time)`
  - `ai_knowledge_chunk(id, kb_code, doc_id, chunk_index, content mediumtext, token_count, metadata json, create_time)`（正文真相源；向量在 Milvus）
- **导入流水线**：上传/拉取文档 → 切块（按字符窗口 + 重叠，可配）→ `EmbeddingProvider.embed` → `VectorStore.upsert(nebula_kb, ...)` + 落 `ai_knowledge_chunk`；`ai_knowledge_document.status` 标 索引中/完成/失败。
- **检索工具** `KnowledgeSearchToolDefinition implements ToolDefinition`（放 `nebula-sdk-ai-rag`，`code=knowledge_search`，`category=search` 业务工具，`params={kbCode, query, topK}`）：`invoke = embed(query) → search(VectorQuery{collection:nebula_kb, partition:kbCode, topK, minScore}) → 返回 [{content, score, metadata}]`。注册即经 `ToolRegistrySynchronizer` 进 `ai_tool`，**流程 TOOL / AGENT_REACT 节点与 Copilot 均可用**。
- **管理端**：`nebula-service-manager` 加 `KnowledgeBaseAdminController`（`/admin/ai-knowledge`，KB CRUD + 文档导入/重建/删除），权限 `manager:ai-knowledge:*`，照 `FlowAdmin`/`ModelProfileAdmin` 模板（camelCase）。

### 9.2 场景② Flow Copilot few-shot（driver 前置召回，推荐）
- **注入点**：`FlowCopilotService` 在 `buildAiRequest(...)` 之后、进 `loop(...)` 之前，用本轮 `prompt` embed → `search(VectorQuery{collection:nebula_flow_example, partition:owner, topK:3, minScore})` → 把命中的"相似流程案例（名称 + 节点摘要 + 精简 definition）"拼成一条 `system`（或 `user`）消息追加进 `request.getMessages()`。**默认前置召回而非工具**：少一轮往返、更可控、无需模型学会调工具。
- **入库触发**：`GenerateFlowToolDefinition.invoke` 落库 `ok:true` 后，发**异步事件**（Spring `ApplicationEventPublisher`）→ 监听器 embed 流程摘要 → `upsert(nebula_flow_example, ...)`。存量流程用一次性 backfill 任务补齐。**异步 + try/catch**：索引失败不影响 `generate_flow` 返回。
- **备选**：若要模型自主决定"要不要看示例"，可改为 `flow_example_search` 工具加进 `COPILOT_TOOL_CODES`。默认不用。

### 9.3 场景③ 长期记忆语义召回
- **新增 `VectorLongTermMemory implements LongTermMemory`（放 `nebula-sdk-ai-rag`），不改 `DatabaseLongTermMemory`**。
- **双源**：MySQL `ai_memory` 为正文真相源；Milvus `nebula_memory` 存 `vector + pk(=ai_memory.id) + 过滤字段(agent_code/user_id/conversation_id/mem_type)`。
  - `save/saveAll`：先写 MySQL 得 id → embed content → upsert Milvus（pk=id）。**双写**。
  - `search`：embed `query.text` → `search(VectorQuery{collection:nebula_memory, partition:agentCode, filterExpr: user_id==... && mem_type==..., topK, minScore})` → 拿 pk 列表回 MySQL 补正文 → 填 `MemoryRecord.score`（归一化值）。**`minScore` 生效**。
  - `get/delete/clear`：走 MySQL + 同步删 Milvus。
- **装配互斥**：`nebula.ai.rag.memory.mode=vector` 时 `VectorLongTermMemory` 标 `@Primary`；`AiMemoryAutoConfiguration` 已用 `ObjectProvider<LongTermMemory>.getIfAvailable()` 取单个，天然择一。`mode=db`（默认）时仍用 `DatabaseLongTermMemory`。
- **前置整改**：补 `ai_memory` **正式 DDL** 进 `script/nebula.sql`；把 `AiMemory` 实体 + `AiMemoryMapper` 从 `nebula-service-blog` **提升为 `nebula-sdk-ai-rag` 共享**（blog 改引用），使记忆能力跨服务复用。

### 9.4 场景④ 节点/工具语义检索
- **检索工具** `SearchToolsToolDefinition`（`code=search_tools`，`category=copilot`，`params={query, topK}`）：embed query → `search(VectorQuery{collection:nebula_tool_catalog, filterExpr: item_type=='TOOL', topK})` → 返回候选工具 `[{code, name, description}]`。加进 `FlowCopilotService.COPILOT_TOOL_CODES`，让模型在工具很多时按需检索而非全量塞。
- **索引器** `ToolCatalogEmbeddingIndexer`（`ApplicationRunner`，`@Order(1)`，跟在 `ToolRegistrySynchronizer @Order(0)` 之后跑）：读 `ToolRegistry.all()`（**排除 `category=copilot`**）+ 内置节点类型清单，embed `name+description` → `upsert(nebula_tool_catalog, ...)`。
- **兜底保留** `list_tools`：`search_tools` 未启用/未命中时，Copilot 仍可用全量 `list_tools`。

---

## 十、配置项汇总

`AiProperties` 新增三个嵌套段（`nebula.ai.*`），全部 `@ConditionalOnProperty` + 下游 `@ConditionalOnBean(VectorStore/EmbeddingProvider)`。

```yaml
nebula:
  ai:
    embedding:
      enabled: false                 # 装配 OpenAiEmbeddingProvider 的门控
      code: openai                   # 对应 ai_knowledge_base.embedding_provider
      base-url: https://api.openai.com/v1
      api-key: ""
      model: text-embedding-3-small
      dimension: 1536                # 必须与建库维度一致
      timeout-ms: 30000
      batch-size: 16
      max-retries: 2
    milvus:
      enabled: false                 # 装配 MilvusClientV2/VectorStore 的门控
      host: localhost
      port: 19530
      uri: ""                        # Zilliz Cloud 整串；填了优先于 host/port
      token: ""
      database: default
      secure: false
      connect-timeout-ms: 10000
      collection-prefix: nebula_
      metric: COSINE
      index: HNSW
      hnsw-m: 16
      hnsw-ef-construction: 200
      hnsw-ef: 64
      auto-create-collection: true
    rag:
      knowledge: { enabled: false }              # knowledge_search 工具 + KB 管理面
      few-shot: { enabled: false, top-k: 3, min-score: 0.6 }
      memory:   { mode: db, top-k: 5, min-score: 0.6 }   # db | vector
      tool-search: { enabled: false }            # search_tools 工具 + ToolCatalog 索引器
```

---

## 十一、降级与可靠性（铁律）

| 场景 | 未配置（enabled=false） | 配了但 Milvus 宕机 |
|---|---|---|
| ① knowledge_search | 工具不注册；KB 管理面关闭 | `invoke` catch → 返回 `{ok:false,error}` map（不抛进 Copilot loop） |
| ② few-shot | 跳过召回，用 `CopilotSystemPrompt` 内联示例 | try/catch **静默跳过**，Copilot 照常生成 |
| ③ 记忆 | 用 `DatabaseLongTermMemory`(LIKE) | `search` catch → **LIKE 回退**；`save` 只写 MySQL 并置 `need_reindex=1` |
| ④ search_tools | 工具不注册，`resolveWhitelist` 交集自动只留 `list_tools`（全量兜底） | 退回 `list_tools` |

> **铁律**：few-shot 与记忆检索的任何 Milvus 异常都**不得逸出**到 Flow Copilot / Agent 主循环。索引写入（②④与③的 save）失败均异步/降级，最终一致由第十二章批次4的对账兜底。

---

## 十二、阶段划分

| 批次 | 交付 | 验收 |
|---|---|---|
| **1 基建 + 场景①** | SPI 扩展 + 新建 `nebula-sdk-ai-rag`（`OpenAiEmbeddingProvider` + `MilvusVectorStore` + `MilvusAutoConfiguration` + `CollectionInitializer` + 配置类）+ parent/bom 接线 + `ai_knowledge_*` DDL/实体/切块导入 + `knowledge_search` + manager KB 管理面 | 建库→导入文档切块→`knowledge_search` 返回 topK 命中、score∈[0,1] |
| **2 场景③** | `ai_memory` 正式 DDL + `AiMemory` 实体/Mapper 提升为 rag 共享 + `VectorLongTermMemory` 双写 + score/minScore + 降级 | `mode=vector` 下 recall 走语义、`score` 有值、`minScore` 生效；Milvus 宕机 LIKE 回退 |
| **3 场景②④** | few-shot 异步事件索引 + 存量 backfill + Copilot 前置召回注入；`ToolCatalogEmbeddingIndexer` + `search_tools` | 生成相似需求时提示词含召回案例；工具多时 `search_tools` 命中相关工具 |
| **4 加固** | alias 模型迁移 runbook、`need_reindex` 补偿对账任务、partitionKey 多租户核验、检索指标/告警 | 换模型经 alias 无感切换；对账把漏写向量补齐 |

---

## 十三、风险与权衡

| # | 风险 | 对策 |
|---|---|---|
| R1 | milvus 重依赖污染纯内核 | 独立模块 `nebula-sdk-ai-rag`；内核只留 SPI |
| R2 | embedding 维度/模型漂移 | 物理名嵌维度 + alias 原子切换 + 建库落 model/dimension + 读写维度校验 |
| R3 | Milvus↔MySQL 双写不一致 | 正文以 MySQL 为真相源；写失败置 `need_reindex` + 批次4 对账补偿 |
| R4 | 向量库不可用拖垮主链路 | 第十一章铁律：few-shot/记忆异常静默降级，不逸出主循环 |
| R5 | embedding 成本/延迟 | 批量 embed（batch-size）；入库异步；可加正文哈希缓存避免重复 embed |
| R6 | 多租户数据串味 | partitionKey（kb_code/agent_code/owner）+ 检索强制标量过滤（user_id 等） |
| R7 | copilot 工具污染（search_tools 进 ai_tool 面板） | `category=copilot`，`list_tools`/前端工具面板按 `category!=copilot` 过滤（沿用第八章 R4） |
| R8 | 已有 `ai_memory` 无 DDL / 实体在 blog | 批次2 先补 DDL 并把实体/Mapper 提升为 rag 共享再改造 |

---

## 十四、端到端验证（批次1）

1. 部署 Milvus standalone；配 `nebula.ai.milvus.enabled=true` + `nebula.ai.embedding.enabled=true`（填向量模型端点/密钥/维度）+ `nebula.ai.rag.knowledge.enabled=true`。
2. 启动服务，`CollectionInitializer` 建出 `nebula_kb`（HNSW/COSINE，维度=配置 dimension）。
3. 经 KB 管理面建知识库 → 导入一篇文档 → 观察 `ai_knowledge_document.status=完成`、`ai_knowledge_chunk` 有行、Milvus `nebula_kb` 有对应向量。
4. 调 `knowledge_search`（或经流程 TOOL 节点 / Copilot）→ 返回 topK 切片，`score∈[0,1]` 降序，语义相关。
5. **降级验证**：停掉 Milvus → `knowledge_search` 返回 error map 而非抛异常；把 `milvus.enabled=false` → 工具不注册、Copilot 仍正常生成流程。

---

## 关联文档

- [对话式流程生成与Agent派生设计.md](./对话式流程生成与Agent派生设计.md) —— 第八章 RAG/向量占位 SPI 蓝本；Flow Copilot（本设计场景②④的宿主）
- [智能体设计.md](./智能体设计.md) —— `ai_flow`/`ai_agent` 三表、`LongTermMemory`/记忆装配（场景③宿主）
- [跨实例迭代层设计.md](./跨实例迭代层设计.md) —— 迭代链 carry-over（与记忆语义召回互补）
- DDL 风格参照 `script/nebula.sql`；工具 seam 参照 `nebula-sdk-ai-flow` 的 `ToolRegistrySynchronizer`
