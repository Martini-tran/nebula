# nebula 对话式流程生成与 Agent 派生（Flow Copilot）设计 v1

> 本文档回答一个具体问题：
> **"想让用户用自然语言对话就生成一条 AI 流程（`ai_flow`）、并顺手派生一个 Agent（`ai_agent`），应该建一个什么样的后端？"**
>
> 核心结论先说：**新建一个独立的流式编排端点 `POST /admin/ai-flow/copilot/stream`，后端自建一个"混合流式工具循环"驱动一个固定的"流程设计助手"**。助手把"生成流程 / 派生 Agent / 查节点类型 / 查工具 / 查模型档案"实现为一组 `ToolDefinition` 工具，由模型自主选调；生成类工具**直接调 `FlowAdminService.save` / `AiAgentAdminService.create` 落库**，SSE 额外推 `flow` / `agent` 事件让前端画布一键回显。
>
> **不复用**现有通用对话端点 `/admin/ai-chat/stream`（那只是裸透传，无工具、不落库），**也不复用** `DefaultToolCallingService`（它受全局开关制约、且不吐进度事件）。
>
> 知识库（向量/RAG）与 MCP 本期**仅给出 SPI 与接入点设计，不实现运行时**——但接入点与业务工具共用同一个 `ToolRegistry` seam，将来落地无需改动主链路。

---

## 〇、先厘清：这个"助手"是什么，不是什么

| 维度 | 是 | 不是 |
|---|---|---|
| 形态 | 一个**写死在代码里**的 function-calling 助手（固定系统提示词 + 固定工具集） | 不是一个 `ai_flow` 自举（不把助手本身建成流程/Agent 去跑） |
| 产物 | **直接落库**的 `ai_flow` 三表 / `ai_agent` 定义 | 不是只返回一段 JSON 让用户自己复制 |
| 会话 | **无状态**（前端携带 `messages` 多轮） | 不引 `conversationId` 会话表 |
| 与画布关系 | 生成后前端**就地 `flowToGraph` 回显 / 跳编辑器加载** | 不改画布 codec，不改整图覆盖式保存链路 |
| 落库入口 | 复用 `FlowAdminService.save(FlowDefinition)` 编程式落库 | 不新写一套 DSL/Builder（POJO 链式 setter 已够用） |

> **关键认知**：这套底层基建（`AiService` function-calling、`ToolRegistry`、`FlowAdminService.save` 编程式落库）几乎是为此量身定做的。**缺的只有"意图 → `FlowDefinition` POJO → 落库"这一层**，以及知识库/MCP 两块空地。所以本设计的重心不是造轮子，而是**把已有轮子用工具的形式串起来，并解决"同步工具循环如何嵌进 SSE 流"这一个真正的工程难点**。

---

## 一、三个硬约束（决定方案走向，均已核验源码）

这三条是方案里所有非直觉决策的根因，先钉死。

### 约束 1：`nebula.ai.toolCalling.enabled` 默认 `false` → 不能直接复用 `DefaultToolCallingService`

`AiProperties.ToolCalling.enabled` 默认 `false`。`DefaultToolCallingService.run()` 在开关关闭或白名单为空时，`resolveWhitelist()` 返回空集，`run()` 随即**退化为 `aiService.chat(request)`——永不下发 tools**。

```java
// DefaultToolCallingService.resolveWhitelist()
if (!config.isEnabled() || allowedToolCodes == null || allowedToolCodes.isEmpty() ...) {
    return Set.of();   // ← 空白名单 → run() 退化为普通 chat，工具永远不会被调用
}
```

> **结论**：Copilot **自建工具循环 driver**，自己 `request.setTools(...)`、自己决定下发哪些工具，**不依赖全局 `toolCalling.enabled`**。`DefaultToolCallingService` 仅作为"治理逻辑（白名单二次校验/超时/异常隔离/审计）"的**照抄蓝本**，不直接调用。

### 约束 2：流式路径不解析 `tool_calls` → "判断要不要调工具"必须走同步 `chat`

`OpenAiChatProvider` 两条路径的能力**不对称**：

| 路径 | 方法 | 是否解析 `tool_calls` |
|---|---|---|
| 同步 | `chat` → `parseResponse` | ✅ 归一化为 `result["toolCalls"]=[{id,name,arguments}]` + `result["assistantMessage"]` |
| 流式 | `stream` → `readStream`/`extractDelta` | ❌ 只取 `choice.delta.content`，完全不看 `tool_calls` |

```java
// OpenAiChatProvider.extractDelta()：流式只提取文本增量，tool_calls 被无视
if (choice.get("delta") instanceof Map<?, ?> delta && delta.get("content") != null) {
    return String.valueOf(delta.get("content"));
}
return "";
```

> **结论**：工具循环里"这一轮模型是否要调工具"的判断，**无论如何都得走同步 `chat`**（流式拿不到 tool_calls）。流式只能用于**已确定不再调工具的终止轮文本**。这是本方案最核心、不可绕过的工程约束，直接决定了第二章的控制流。

### 约束 3：`ToolRegistry` 用 `ObjectProvider<ToolDefinition>` 聚合 → 生成类工具放 manager 模块

`ToolRegistry` Bean 由 `FlowAutoConfiguration` 用 `ObjectProvider<ToolDefinition>.orderedStream()` 装配，**聚合 Spring 容器内所有 `ToolDefinition` Bean**。manager 服务 `@SpringBootApplication` 默认扫 `com.nebula.manager.**`。

```java
// FlowAutoConfiguration
@Bean @ConditionalOnMissingBean
public ToolRegistry toolRegistry(ObjectProvider<ToolDefinition> definitions) {
    return new ToolRegistry(definitions.orderedStream().toList());  // ← 容器内所有 ToolDefinition Bean
}
```

> **结论**：把"生成流程 / 派生 Agent"等**需要注入 manager Service** 的工具，放在 **manager 模块**、标 `@Component implements ToolDefinition`。它们：① 被 manager 上下文里的 `ToolRegistry` 自动聚合；② 能 `@Autowired` 注入 `FlowAdminService`/`AiAgentAdminService`。SDK 反向不依赖 manager，**依赖方向正确**，无需任何额外接线。
>
> **副作用（须处理）**：`ToolRegistrySynchronizer`（`@Order(0)` ApplicationRunner）会把这些工具**自动镜像进 `ai_tool` 表**，从而出现在流程编辑器的"工具节点"下拉里（对用户无意义）。对策见第七章 R4：统一 `category()="copilot"` 做过滤。

---

## 二、核心：混合流式工具循环

由约束 2，Copilot 的循环是**"判断轮同步 + 终止轮流式"**的混合体，跑在单个 SSE 独立线程内、串行推进。

### 2.1 控制流

```text
buildAiRequest(req)：注入 system 提示词 + 历史 messages + 本轮 prompt
setTools(copilotTools)：下发本 copilot 固定工具集（不看 toolCalling.enabled）

for iter in 0 .. maxIterations:
    resp = aiService.chat(request)              # 同步——才能拿到 toolCalls（约束2）
    toolCalls = resp["toolCalls"]

    if toolCalls 为空:                           # ── 终止轮 ──
        把 resp["content"] 按小片循环 → sink.delta(片段)   # B1：分片模拟打字机
        sink.done(resp)
        return

    appendAssistantMessage(request, resp)       # 先追加带 tool_calls 的 assistant 消息（OpenAI 顺序约定）
    for call in toolCalls:                       # ── 非终止轮：执行工具 ──
        sink.toolCall({status:start, name, arguments})
        toolMsg = executeOne(call, whitelist, ctx)         # 复用治理：白名单二次校验/超时/异常隔离/审计
        sink.toolCall({status:done, name, resultBrief})
        if 产物是落库结果:                        # generate_flow.ok==true / derive_agent.ok==true
            sink.flow({flowCode, version, name, definition?}) 或 sink.agent({agentCode, id, name})
        request.messages.add(toolMsg)            # tool 结果回灌，进入下一轮判断

sink.error({message: "达到迭代上限"})            # 兜底
```

### 2.2 终止轮文本推送：B1（默认）vs B2

| 方案 | 做法 | 取舍 |
|---|---|---|
| **B1（默认）** | 终止轮的 `resp["content"]` 已是完整最终回复，driver 按字符窗口（如每 20~40 字/按标点）切片循环 `sink.delta(片段)`，再 `sink.done(resp)` | 省一次 LLM 调用、实现简单。不是真逐字流，但"生成完流程后一句话总结"往往很短，够用 |
| **B2（可选）** | 终止轮不复用 `resp`，对同一份 messages 再发一次 `aiService.stream`（此时模型已无工具可调），`onDelta` 转 `sink.delta` | 真逐字流，但多一次 LLM 调用；且需 `toolChoice="none"` 强制模型不再尝试调工具 |

> `FlowCopilotService` 暴露 `boolean streamFinalTurn` 开关（默认 `false`=B1），便于后续切 B2。

### 2.3 类结构（均在 manager 模块）

| 类 | 包 | 职责 |
|---|---|---|
| `FlowCopilotController` | `com.nebula.manager.ai` | SSE 端点。**照抄 `AiChatAdminController` 的 `SseEmitter` + 独立线程池 + `AtomicBoolean terminated` 模板**；提交任务前在**请求线程**里取 `Long uid = UserContext.getUserId()` 传进 driver（R1） |
| `FlowCopilotService` | `com.nebula.manager.ai.copilot` | 混合流式工具循环。注入 `AiService`/`ToolRegistry`/`AiProperties`（取 maxIterations/toolTimeoutMs）+ 自建 `ObjectMapper`。方法：`buildAiRequest`/`copilotToolCodes`/`buildToolSchemas`/`executeOne`（照搬治理）/`loop` |
| `CopilotSseSink` | `com.nebula.manager.ai.copilot` | 薄封装：`delta/toolCall/flow/agent/done/error`，内部持 emitter + terminated，写失败置 terminated（照 `AiChatAdminController.SseCallback.send`） |

> **`ToolContext`**：driver 用 `new SimpleToolContext(String.valueOf(uid), req.getConversationId())`，工具内用 `ctx.userId()` 而非 `UserContext`（规避 R1）。

---

## 三、工具集设计

全部放 manager 模块 `com.nebula.manager.ai.copilot.tool`，标 `@Component implements ToolDefinition`，统一 `category()="copilot"`。命名建议短名，`description()` 兼作 LLM function description。

| 工具 | 类型 | 注入 | 职责 |
|---|---|---|---|
| `list_node_types` | 只读 | `List<FlowNodeExecutor>` | 返回 `[{type,name,description}]`。`type` 取 `nodeExecutors.stream().map(type).distinct()`（同 `FlowAdminController.nodeTypes`），`name`/`description` 在工具内写死一份**完整**节点语义（含 START/END/IF/JOIN/LOOP/AGENT/AGENT_REACT，而非只有 PROMPT/TOOL） |
| `list_tools` | 只读 | `ToolAdminService` | 查 `ai_tool` `enabled=1`，**排除 `category=copilot`**，返回 `[{toolCode,name,description,paramsSchema}]`。避免模型把 copilot 自身工具当业务工具塞进流程 |
| `list_model_profiles` | 只读 | `ModelProfileAdminService` | 查 `ai_model_profile` `status=1`，返回 `[{profileCode,name,provider,model}]`。**绝不返回 apiKey**（Service 出参本就掩码） |
| **`generate_flow`** | **写库** | `FlowAdminService` | 核心。`paramsSchema` 对齐 `FlowDefinition`；`invoke` 构造 POJO → 轻量校验 → `save(def)` |
| `derive_agent` | 写库（阶段2） | `AiAgentAdminService` | 派生 Agent。`paramsSchema` 对齐 `AgentSaveRequest`；`invoke` → `create(req)` |

### 3.1 `generate_flow.paramsSchema`（对齐 `FlowDefinition`，让模型输出合法结构）

- 顶层 `required:[flowCode, nodes, edges]`；`name`/`description`/`version`(default 1)/`engineType`(enum DAG|STATE_MACHINE, default DAG)/`defaultProfileCode`（从 `list_model_profiles` 选）。
- `nodes[]` item `required:[nodeCode, nodeType]`：
  - `nodeType` enum(PROMPT/TOOL/START/END/IF/JOIN/LOOP/AGENT/AGENT_REACT)
  - `promptTemplate`（PROMPT 必填，用 `#{var}` 引用上下文键）、`systemPrompt`
  - `profileCode`/`model`/`temperature`
  - `inputMapping`(object<string>：模板变量名→上下文键)、`outputKey`(产物写回上下文键)、`outputMode`(enum TEXT|JSON, default TEXT)
  - `toolCode`(string，TOOL 节点引用的已启用工具编码)
  - `x`/`y`(number，画布坐标)
- `edges[]` item `required:[fromNode, toNode]`：`conditionExpr`(SpEL 出边条件，IF 分流用，可空)。

### 3.2 `generate_flow.invoke` 要点

1. 手工逐字段 `set` 成 `FlowDefinition`（比整体反序列化更稳），**直写顶层扁平字段**（`systemPrompt`/`promptTemplate`/`model`/`profileCode`/`temperature`/`outputKey`/`outputMode`/`inputMapping`）——因**扁平优先**（`FlowDefinitionConverter.flattenNodeConfig` 仅在顶层为空时才从嵌套摊平），最省事且**直接可运行**。
2. `x`/`y` → `node.getNodeConfig().put("__x6", Map.of("x",x,"y",y))`（前端回显坐标，执行器不读）；`toolCode` → `nodeConfig.put("toolCode", ...)`（`ToolNodeExecutor` 读此平铺键）。
3. **轻量校验**（工具内做，失败**返回 `{ok:false, error}` 而非抛异常**，让循环回灌给模型自愈，见 R6）：`nodeCode` 唯一、`edges` 引用的 node 存在、至少一个 START + 一个 END、PROMPT 节点有 `promptTemplate`。
4. 通过则 `flowAdminService.save(def)`（`@Transactional`，落三表 + `evict`），返回 `{ok:true, flowCode, version, name, nodeCount}`。
5. driver 在 `executeOne` 后识别：`toolCall.name()=="generate_flow"` 且产物 `ok==true` → 额外 `sink.flow(...)`，可选把完整 `FlowDefinitionRaw`（camelCase）放进 `definition` 字段供前端就地回显、免二次拉取。

### 3.3 `derive_agent.invoke` 要点（阶段2）

`paramsSchema` `required:[agentCode, flowCode]` + `name`/`description`/`flowVersion`/`inputSchema`/`outputSchema`/`memoryConfig`/`defaultProfileCode`。构造 `AgentSaveRequest` → `aiAgentAdminService.create(req)` → 返回 `{ok:true, agentCode, id, name}`。校验失败同 `generate_flow` 返回 error map。driver 识别后推 `agent` 事件。

---

## 四、系统提示词工程

`FlowCopilotService.buildAiRequest` 里写死一条 `role=system` 消息。要点清单：

1. **身份**：你是 Nebula 流程设计助手，把用户的自然语言需求转成一条**合法的 AI 编排流程**并落库。
2. **前置调用**：若不确定可用节点类型/工具/模型档案，先调 `list_node_types`/`list_tools`/`list_model_profiles`。折中：system 内联**常用节点类型速查**（节点集相对稳定，减少往返），`list_*` 仍暴露供模型深挖动态资源（工具/档案是运行时数据）。
3. **`FlowDefinition` schema 摘要**：`flowCode` 唯一；`nodes[].nodeCode` 流程内唯一；`edges` 用 `nodeCode` 连接；PROMPT 节点必填 `promptTemplate`；`outputKey` 承接产物，下游用 `#{key}` 或 `inputMapping` 引用上游产物。
4. **节点语义速查**：PROMPT(调模型)、TOOL(调注册工具，需 `toolCode`)、START/END(入口/出口，DAG 建议各一)、IF(出边 SpEL 分流)、JOIN(并行汇聚)、LOOP(子图循环)。
5. **双轨约定**：只输出**扁平字段**（`promptTemplate`/`model`/`outputKey`…），**不要**输出 `nodeConfig.llm` 嵌套结构。
6. **坐标约定**：为每个节点给 `x`/`y`，沿主流程从左到右递增（如 x=120,360,600…，同层 y 相同），避免前端画布节点堆叠在原点。
7. **产出纪律**：一次性给全 `nodes`+`edges`；落库成功后用**一句话**说明流程名与节点数，**不复述完整 JSON**。
8. **few-shot**：内联一个最小合法流程（START→PROMPT summarize→PROMPT translate→END）作示例。

---

## 五、SSE 事件契约

在 `AiChatAdminController` 既有的 `delta`/`done`/`error` 之上，新增 `tool_call`/`flow`/`agent` 三种事件。

| 事件 | 触发时机 | payload | 前端处理 |
|---|---|---|---|
| `delta` | 终止轮分片推最终文本 | `{content}` | 追加到当前助手气泡（同 ai-chat） |
| `tool_call` | 每个工具调用开始/结束 | `{status:"start"\|"done", name, arguments?, resultBrief?}` | 渲染"正在调用 X / 已完成"轻量进度 |
| `flow` | `generate_flow` 落库成功 | `{flowCode, version, name, definition?}` | 携 `flowCode` 跳编辑器，或 `flowToGraph(definition)` 就地回显 |
| `agent` | `derive_agent` 落库成功（阶段2） | `{agentCode, id, name}` | 提示已派生，跳 `/ai-agent/agents` |
| `done` | 循环正常结束 | 聚合响应 `{content, role, model, usage, ...}` | 内容已由 delta 拼出，done 仅收尾/停 loading |
| `error` | 循环异常/达迭代上限 | `{message}` | `ElMessage.error`，停 loading |

事件序列示例（"帮我生成一个先总结再翻译的流程"）：

```text
tool_call{start, list_node_types}  →  tool_call{done, list_node_types}
tool_call{start, generate_flow, arguments:{flowCode, nodes:[START, summarize(PROMPT), translate(PROMPT), END], edges}}
tool_call{done, generate_flow}
flow{flowCode:"summarize_translate", version:1, name:"总结后翻译"}
delta{"已为你生成"} delta{"总结后翻译流程"} delta{"，含 4 个节点。"}
done{...}
```

---

## 六、会话模式：无状态

沿用 ai-chat 模式：前端携带 `messages` 回传，`buildAiRequest` 拼 `system + history + 本轮 prompt`。

- **工具循环内追加的 `assistant(tool_calls)`/`tool` 中间消息不跨轮保持**——只活在本轮 `request.messages`，循环结束即丢弃；前端只保存最终 `assistant` 文本作下一轮 history。
- **代价**：下一轮模型看不到上一轮"调了哪个工具、生成了哪个 flowCode"的机器细节。**缓解**：终止轮的自然语言里已含"已生成 xxx 流程"，这条进 history 足够模型下一轮引用（如"在刚才的流程上加一个校对节点"）。若不够，前端可在 `flow` 事件后把 `flowCode` 拼进下一轮提示。
- `conversationId` 仍透传给 `SimpleToolContext`/审计维度，但不驱动落库。
- **备选（本期不做）**：若将来无状态明显不够，再上 `ai_copilot_conversation`/`ai_copilot_message` 两表做服务端多轮（DDL 见附录）。

---

## 七、风险与取舍

| # | 风险 | 对策 |
|---|---|---|
| **R1** | **`UserContext` ThreadLocal 丢失**：SSE 在独立线程执行，`UserContext.getUserId()` 不跨线程 | controller 在**请求线程**先 `Long uid = UserContext.getUserId()` 传进 driver；driver 执行工具前 `UserContext.set(uid)`（`finally UserContext.clear()`），或工具用 `ctx.userId()`。（探索：`save`/`create` 未直接强依赖 UserContext，仍保险回填） |
| **R2** | **Sa-Token 注解在异步线程**：`@SaCheckPermission` 由容器请求线程校验（进方法前已过），OK；工具内若再 `StpUtil.checkPermission` 异步线程可能取不到 token | 权限统一在端点入口 `manager:ai-copilot:stream` 兜，不在工具内二次校验 |
| **R3** | **流式拿不到 tool_calls**（约束2） | 已规避：判断轮同步、终止轮分片/可选真流式，不可绕过 |
| **R4** | **copilot 工具污染流程编辑器工具面板**：经 `ToolRegistrySynchronizer` 进 `ai_tool` 表 | 统一 `category()="copilot"`；`list_tools` 与前端工具面板都按 `category!=copilot` 过滤 |
| **R5** | **权限绕过粒度**：工具编程式调 `save`/`create` 绕过 `manager:ai-flow:save` 的 controller 拦截 | 本期以端点权限 `manager:ai-copilot:stream` 作唯一闸门；若需更细，工具内显式 `StpUtil.checkPermission`（受 R2 限制） |
| **R6** | **模型生成非法流程**：重复 nodeCode / 悬空 edge / PROMPT 缺 promptTemplate / 无 START-END | `generate_flow` 内轻量校验，失败回灌 error 让模型自愈（不抛异常），配合 `maxIterations` 限重试 |
| **R7** | **迭代成本**：每判断轮一次同步 LLM 调用，工具越多往返越多 | `maxIterations` 复用 `nebula.ai.toolCalling.maxIterations`(默认5)；system 引导一次性给全流程 |
| **R8** | **flowCode 覆盖**：`save` 按 flowCode upsert（节点/边全删再插）会覆盖线上流程 | `generate_flow` 对已存在 flowCode 默认返回 error 让模型换 code，或加 `overwrite:boolean` 参数显式确认 |
| **R9** | **SSE 超时**：一轮含多次 LLM+工具，可能超 ai-chat 的 300s | 新端点 timeout 设更长（如 600s），driver 每步推事件保活 |
| **R10** | **ObjectMapper 版本**：manager Web 层 Jackson 3，容器无 Jackson 2 ObjectMapper Bean | 工具/service 内**自建** `new ObjectMapper()`（同现有 ServiceImpl 惯例），勿 `@Autowired` |

---

## 八、知识库/RAG 与 MCP —— 仅 SPI 占位（本期不实现运行时）

两块目前是**空地**（无 embedding/向量库；MCP 仅有 CRUD、无运行时客户端）。本期只给接口与接入点，**运行时留待后续批次**。关键在于：二者都挂在与业务工具**同一个** `ToolDefinition`/`ToolRegistry` seam 上，落地时**不触碰主链路**。

### 8.1 知识库/RAG

- **SPI（已落地占位接口，`com.nebula.common.ai.rag`，无实现/无 Bean）**：
  - `EmbeddingProvider`：`code()` / `List<float[]> embed(List<String> texts)` / `int dimension()`
  - `VectorStore`：`upsert(chunks, embeddings)` / `List<VectorChunk> search(String kbCode, float[] queryVector, int topK)` / `deleteByDoc(kbCode, docId)`
  - `VectorChunk`（record）：`kbCode / docId / chunkIndex / content / score / metadata`，对应 `ai_knowledge_chunk` 一行
- **表设计**（仅设计）：
  - `ai_knowledge_base(id, kb_code UNIQUE, name, embedding_provider, dimension, status, create_time, update_time)`
  - `ai_knowledge_chunk(id, kb_code, doc_id, chunk_index, content TEXT, embedding <JSON/BLOB 存 float[]>, metadata JSON, create_time)`
  - 向量列：MySQL 无原生向量类型，先用 JSON/BLOB 存 `float[]`、检索走应用层余弦；或后续接 pgvector/Milvus（接入点在 `VectorStore` SPI）。
- **检索工具**（接口设计）：`KnowledgeSearchToolDefinition implements ToolDefinition`，`code()="knowledge_search"`，`paramsSchema={kbCode, query, topK}`，`invoke = embed(query) → VectorStore.search → 返回 top-k chunks`。**接入点**：注册为 `@Component ToolDefinition` 即挂进 `ToolRegistry`（与业务工具同 seam），流程 TOOL 节点或 copilot 均可用。

### 8.2 MCP

- **SPI（已落地占位接口，`com.nebula.common.ai.mcp`，无实现/无 Bean）**：
  - `McpTransport`：`type()` / `Map<String,Object> request(method, params)` / `close()` —— streamable-http / sse 实现占位（stdio 非本期范围）
  - `McpClient`：`serverCode()` / `List<McpToolSpec> listTools()` / `Object callTool(String toolName, Map<String,Object> args)`
  - `McpToolSpec`（record）：`name / description / inputSchema`
- **适配器**（接入点设计，本期不建实体类）：`McpToolAdapter implements ToolDefinition` —— 把一个 MCP server 暴露的工具包成 `ToolDefinition`（`code()="mcp:<serverCode>:<toolName>"`，`paramsSchema()` 取 `McpToolSpec.inputSchema()`，`invoke` 转发 `McpClient.callTool`）。启动时按已登记的 MCP server（`McpServerAdmin*` 已有管理端 CRUD）动态注册这些适配器 Bean → 经 `ToolRegistrySynchronizer` 自动进 `ai_tool` → 流程/copilot 可用。
- 本期只留接口 + 接入点说明，**不写 transport/client 运行时**。

---

## 九、阶段划分

| 阶段 | 交付 | 验收 |
|---|---|---|
| **1 主链路** | `CopilotStreamRequest` DTO + `FlowCopilotController`(SSE) + `FlowCopilotService`(混合流式循环) + `CopilotSseSink` + 工具{`list_node_types`,`list_tools`,`list_model_profiles`,`generate_flow`} + system 提示词 + 权限点 `manager:ai-copilot:stream` + 前端 `copilotStreamApi` + `AiChatPanel` 模式开关与 `flow`/`tool_call` 事件处理 | 对话生成流程并落 `ai_flow` 三表、前端画布回显 |
| **2 派生 Agent** | `DeriveAgentToolDefinition` + driver 推 `agent` 事件 + 前端 `agent` 事件处理 + system 增派生指引 | 对话中派生 `ai_agent` |
| **3 RAG/MCP 占位** | `EmbeddingProvider`/`VectorStore`/`McpClient`/`McpTransport`/`McpToolAdapter` 空接口 + 表设计文档 + 接入点说明 | 编译通过，接口/文档就绪，不写实现 |

---

## 十、端到端验证

1. 前端编辑器第四栏切"生成流程"模式，输入"帮我生成一个先总结再翻译的流程"。
2. **期望 SSE 序列**（浏览器 Network EventStream 或后端日志）：`tool_call{generate_flow, arguments:{nodes:[START, summarize(PROMPT), translate(PROMPT), END], edges}}` → `flow{flowCode, version:1, name}` → 多个 `delta` → `done`。
3. **DB 校验**：
   - `SELECT * FROM ai_flow WHERE flow_code=<code>` → 1 行，`engine_type=DAG`。
   - `SELECT node_code,node_type,prompt_template,output_key FROM ai_flow_node WHERE flow_code=<code>` → START / summarize(PROMPT，有 promptTemplate，outputKey=summary) / translate(PROMPT，promptTemplate 用 `#{summary}`) / END。
   - `SELECT from_node,to_node FROM ai_flow_edge WHERE flow_code=<code>` → START→summarize→translate→END。
   - `SELECT node_config FROM ai_flow_node ...` → 含 `__x6:{x,y}`。
4. **画布回显**：`flow` 事件带 `definition` 则 `flowToGraph` 就地渲染 4 节点连线；否则跳编辑器 `getFlowDetailApi(flowCode)` 拉取后回显，坐标来自 `__x6`。
5. **跑通验证（可选）**：`POST /manager/admin/ai-flow/flows/<code>/run` body `{input:{text:...}}`，`nodeResults` 有 summarize/translate 产物 → 证明生成的流程扁平字段直连引擎、可执行。
6. **阶段2**：追加输入"基于这个流程派生一个 Agent" → `tool_call{derive_agent}` + `agent{agentCode,id}`，`SELECT * FROM ai_agent WHERE agent_code=<code>` 有行、`flow_code` 指向刚生成的流程。

---

## 附录：备选 copilot 会话表 DDL（仅设计，本期不建）

若将来放弃无状态、做服务端多轮：

```sql
CREATE TABLE ai_copilot_conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id VARCHAR(64) NOT NULL,
  user_id BIGINT,
  title VARCHAR(255),
  last_flow_code VARCHAR(64),
  status TINYINT DEFAULT 1,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_conv (conversation_id)
);
CREATE TABLE ai_copilot_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id VARCHAR(64) NOT NULL,
  seq INT NOT NULL,
  role VARCHAR(16) NOT NULL,
  content MEDIUMTEXT,
  tool_calls JSON,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_conv_seq (conversation_id, seq)
);
```

---

## 关联文档

- [智能体设计.md](./智能体设计.md) —— `ai_flow`/`ai_agent` 三表、状态机内核、graph_snapshot、转移裁决
- [计划-执行节点设计.md](./计划-执行节点设计.md) —— `AgentReactNodeExecutor`（本 copilot 生成的流程里 AGENT_REACT 节点即出自此）
- [编排回调Webhook设计.md](./编排回调Webhook设计.md) —— 生成的流程/Agent 产物如何 POST 到外部消费端
- [跨实例迭代层设计.md](./跨实例迭代层设计.md) —— 派生的 Agent 如何接迭代链做系列递推
