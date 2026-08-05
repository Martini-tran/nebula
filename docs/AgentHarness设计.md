# nebula Agent Harness（统一运行外壳）设计 v1

> 本文档是 [智能体设计.md](./智能体设计.md) 的增补，回答一个具体问题：
>
> **「如何让所有 AI Agent 都遵循同一套运行外壳（Harness），而不是各写各的？」**
>
> **核心结论先说：**
>
> 1. **Harness 不需要新建，它已经存在**——就是 `AgentEngine`。本设计做的是**就地升格**，不是再包一层 `AgentHarness` 门面（两个门面并存必然产生「谁是权威」的歧义）。
> 2. 现状真正的问题不是「没有标准」，而是**标准拦不住逆行**。全仓仅 2 个文件绕过了 Harness，但其中 `FlowCopilotService` 一个文件就照抄了约 400 行治理逻辑。**它绕过是有正当技术理由的**（全局开关一票否决 + 无法吐进度事件），所以先补 SDK 能力，再谈收口——**能力不补齐，任何收口都是逼人违规**。
> 3. **不把 Copilot 压成 1 节点 `AGENT_REACT` flow**。看似最统一，实则有四条真实代价，其中 `generate_flow` 写 `ai_flow` 表会造成**自举与版本锁定冲突**，是致命的。改为 Agent **双形态**：`FLOW` + `CONVERSATIONAL`，两者共用同一门面 / 同一记账 / 同一事件 / 同一提示词解析 / 同一工具治理。**收口的对象是「都从 AgentEngine 进」，不是「都变成一张图」。**
> 4. 收口靠 **ArchUnit 编译期阻断**，不靠规范和评审。规则必须按**全限定类名黑名单**点名，绝不能按包路径白名单——`ToolDefinition` / `ToolContext` / store SPI / DTO 全在 `com.nebula.common.ai.**` 下，一条 `resideInAPackage` 会炸掉整个 manager。
>
> **本批次交付**：设计与落地批次划分。**不含实现代码**。
>
> **本文档不重复**：`ai_flow` / `ai_agent` 三表结构、状态机内核语义、崩溃恢复四铁律——见 [智能体设计.md](./智能体设计.md)。

---

## 〇、先厘清

动手前必须消除四组歧义，否则整个方案会走偏。

| 维度 | **是** | **不是** |
|---|---|---|
| Harness | `AgentEngine` **就地升格**成的统一执行门面 | 新建一层 `AgentHarness` 包装类 |
| 收口对象 | 所有 Agent 都**从 `AgentEngine` 进** | 所有 Agent 都变成 `ai_flow` 里的一张图 |
| 护栏粒度 | 按**全限定类名**逐个点名的黑名单 | 按包路径 `com.nebula.common.ai..` 的白名单 |
| 禁止的行为 | 业务模块**调用 AI 运行时** | 业务模块 CRUD `ai_*` 表（**完全合法**，manager 本职） |
| 流式承诺 | 进度事件全覆盖 + token 增量**分级**兑现 | 「所有 Agent 都能逐字流式」（Provider 层有硬约束） |
| `AbstractAgent` | 与硬收口**语义冲突**的死代码，本期删除 | 可以留着「以后也许有用」的扩展点 |

> **关键认知**：本设计的绝大部分工作量不在「建 Harness」，而在**把 Copilot 绕过的两个理由从 SDK 层根除**。理由不除，收口就是把违规从明处赶到暗处。

---

## 一、背景与现状

### 1.1 已经很好的部分

`nebula-sdk-ai` / `nebula-sdk-ai-flow` / `nebula-sdk-ai-rag` 三模块分层清晰，贯穿全仓的架构律非常干净：

> **SPI 定义在纯内核 `nebula-sdk-ai`，实现落在卫星模块，实现缺失时优雅降级为内存版。**

`RunStateStore` / `AgentInstanceStore` / `IterationChainStore` / `WebhookDeliveryStore` / `FlowDefinitionRepository` / `ModelProfileRepository` / `AgentDefinitionRepository` 全部遵循此律。`AgentEngine` 已具备版本锁定（`graph_snapshot`）、崩溃恢复四铁律、挂起唤醒 CAS、子 Agent 递归深度与成环治理。**这套内核是可靠的，本设计不推翻任何一条。**

### 1.2 三处逆行 + 一处死代码

| # | 位置 | 问题 | 为什么会这样 |
|---|---|---|---|
| 1 | `nebula-service-manager/.../ai/copilot/FlowCopilotService.java`（548 行） | 手写完整工具循环，**照抄** `DefaultToolCallingService` 的白名单 / 超时 / 异常隔离 / 审计约 400 行；自建 `copilot-tool-N` 线程池；提示词写死在 `CopilotSystemPrompt.java`（63 行 text block）；硬编码 `COPILOT_TOOL_CODES` | **两条理由都成立**：① 被全局开关 `nebula.ai.tool-calling.enabled` 一票否决；② `DefaultToolCallingService` 无法吐 SSE 进度事件。**这不是偷懒，是 SDK 能力缺失** |
| 2 | `nebula-service-manager/.../ai/chat/AiChatAdminController.java`（333 行） | Controller **直调** `AiService.stream`，无 Service 层；两个重复的 `AiCallback` 内部类；无记忆、无落库、无过滤器链、无调用日志 | 需求最初只是「裸透传」，随手写在 Controller 里，之后无人回收 |
| 3 | `ai_prompt` 表 + `PromptAdminService` + 后台页面 | **运行时无任何代码读取该表**。所有提示词要么内联在 Java，要么内联在 `ai_flow_node.node_config` | 表和管理端先行落地，运行时解析链一直没接 |
| 4 | `nebula-sdk-ai/.../agent/AbstractAgent.java` + `Agent` + `AgentRegistry` | 全仓**零子类、零调用**，`agentRegistry` bean 永远收集到空列表 | 早期设计的「代码定义 Agent」扩展点，被 `ai_agent` 数据定义路线取代后未清理 |

> **必须强调第 4 条的严重性**：`AbstractAgent.chat` 直接调 `aiService.chat`，`chatWithTools` 直接调 `toolCallingService.run`——**这正是硬收口要禁止的行为**。而它位于 `com.nebula.common.ai..` 内核包中，ArchUnit 的业务侧规则**够不着它**。留着它，等于在内核里内置一条官方认证的绕过通道，**硬收口就是假的**。

### 1.3 改造面比想象中小

全仓 grep `nebula-services` 下直接依赖 `AiService` / `ToolCallingService` 的文件：**只有上表第 1、2 两个**。

这意味着：**收口的技术成本是可控的，真正的风险在护栏规则的措辞（见 R1），而不在改造量。**

---

## 二、硬约束（决定方案走向，均已核验源码）

以下九条全部逐行读过源码确认，是本设计所有取舍的依据。

**C1. 流式当前结构上无法承载工具调用。**
`OpenAiChatProvider.extractDelta` 只取 `choice.delta.content`，**完全不累积 `delta.tool_calls` 分片**；`readStream` 的聚合结果只 put `content` / `role` / `model` / `finishReason` / `usage`，**不含 `toolCalls` 与 `assistantMessage`**——而后两者正是 `DefaultToolCallingService` 驱动循环所必需的。
→ 参见 `OpenAiChatProvider.java` 的 `extractDelta` 与 `readStream`。
→ **推论：在批次 8 之前，带工具的 Agent 中间轮只能同步调用，这是 Provider 层的硬边界，Harness 绕不过去。**

**C2. 工具调用开关是全局一票否决。**
`DefaultToolCallingService.resolveWhitelist` 首行即 `if (!config.isEnabled() || ...) return Set.of()`，且 `nebula.ai.tool-calling.enabled` 默认 `false`。一个全局 boolean 决定所有 Agent 的生死。
→ **推论：Copilot 除了 fork 别无选择。这是必须在 SDK 修的第一个洞。**

**C3. `TransitionListener` 没有「开始执行」回调。**
现有六个回调为 `onStateSucceeded` / `onStateRetry` / `onTransition` / `onTerminal` / `onInstanceFailed` / `onSuspended`——**全部是事后记账**。一个执行 30 秒的节点，在它开始时前端拿不到任何信号。
→ **推论：进度可观测必须给内核加 `onStateStarted`。这是本设计对内核的唯一侵入。**

**C4. `DefaultAiService.stream` 绕过全部治理。**
`stream` 方法体仅 6 行：判空 → `applyDefaults` → `resolver.resolve(...).stream(...)`。相比 `chat` 缺失：过滤器链、`publishBefore`/`publishAfter` 事件、`persistConversation` 会话落库、`writeLog` 调用日志。
→ **推论：这是 `AiService` 自身的实现缺陷（stream 与 chat 治理不对称），与 Harness 是两件事，应作为独立前置批次修复。**

**C5. `AgentEngine.run` 对对话型 Agent 存在写放大。**
`run` 强制执行 `StartInputValidator` 入参校验，并把编译后的 `FlowDefinition` 序列化进 `graph_snapshot`。对话型 Agent 是无状态多轮（前端携带 messages），10 轮对话 = 10 条实例 + 10 份全图快照。
→ **推论：对话型不能复用 FLOW 的实例语义。**

**C6. 历史 messages 在 flow 模型中无处安放。**
`AgentReactNodeExecutor` 从 `node.promptTemplate` / `systemPrompt` 构造 messages，**没有「把调用方传入的历史消息原样注入」的入口**。历史 messages 是**运行时入参**而非节点配置，塞进 `inputs` 再走模板渲染是 JSON 转义地狱。

**C7. Copilot 若自身是一张 flow，将产生自举与版本锁定冲突。**
`generate_flow` / `derive_agent` 两个工具会**写 `ai_flow` / `ai_agent` 表**。若 Copilot 本身建成 `ai_flow` 的一行，就出现「一个 flow 的运行实例在写 flow 表」，与 `graph_snapshot` 的版本锁定语义直接冲突。
→ **推论：C5 + C6 + C7 共同否决「Copilot 压成 1 节点 flow」的方案。**

**C8. 两个节点执行器的请求装配逻辑逐行重复。**
`PromptNodeExecutor` 与 `AgentReactNodeExecutor` 的模型档案三层合并、变量池装配、`firstNonBlank` / `firstNonNull` 等私有方法**完全一致**，仅最后一行 `aiService.chat(...)` vs `toolCallingService.run(...)` 不同。
→ **推论：提示词治理若不先抽公共装配器，就得写两遍。**

**C9. 全仓无 ArchUnit、无 maven-enforcer。**
`nebula-sdk-ai/pom.xml` 的 test 依赖只有 `junit-jupiter`。护栏是**纯新建**，无既有基建可复用。

---

## 三、总体架构

```text
┌──────────────────────── 业务侧（nebula-services / nebula-apis）─────────────────────────┐
│                                                                                        │
│   AiAgentAdminService      FlowCopilotService       AiChatService      业务 Service      │
│          │                        │                       │                 │           │
└──────────┼────────────────────────┼───────────────────────┼─────────────────┼───────────┘
           │                        │                       │                 │
     ╔═════▼════════════════════════▼═══════════════════════▼═════════════════▼═════════╗
     ║              【唯一入口】AgentEngine —— Harness 门面                              ║
     ║   run(def, inputs, …, sink)   converse(def, messages, …, sink)                   ║
     ║   signal(instanceId, …)       resume(instanceId, …)                              ║
     ║                       按 AgentDefinition.agentShape 分派                          ║
     ╚═══════════╤══════════════════════════════════════════════╤══════════════════════╝
                 │ FLOW                            CONVERSATIONAL │
     ┌───────────▼─────────────────┐              ┌──────────────▼──────────────────┐
     │ StateMachineOrchestrator    │              │  ToolCallingService             │
     │ DagOrchestrator             │              │  （单 ReAct 回合，无 graph）     │
     │  └─ Prompt/Tool/Agent/Loop  │              │                                 │
     │     /AgentReact/End 执行器   │              │                                 │
     └───────────┬─────────────────┘              └──────────────┬──────────────────┘
                 │                                               │
     ╔═══════════▼═══════════════════════════════════════════════▼══════════════════════╗
     ║                        两路共汇的 Harness 标准能力                                 ║
     ║  AgentEventSink（进度/流式）  PromptRepository（提示词）  AiRequestAssembler（装配） ║
     ║  AgentInstanceStore（记账）   AgentMemory（记忆）        Budget/Tool Policy（治理） ║
     ╚═══════════════════════════════╤══════════════════════════════════════════════════╝
                                     │
                          ┌──────────▼───────────┐
                          │ AiService（含过滤器链）│──► AiProvider ──► 厂商
                          └──────────────────────┘

  ┌────────────────────────────────────────────────────────────────────────────────────┐
  │ 🔒 ArchUnit 护栏环（nebula-sdk-ai-archtest）                                        │
  │   业务侧 ✗→ AiService / ToolCallingService / FlowEngine / *Orchestrator             │
  │   业务侧 ✓→ AgentEngine（唯一入口）、ToolDefinition（反向回调）、store SPI（持久化）  │
  │   @RestController ✗→ AgentEngine（Controller 只做传输编解码）                        │
  └────────────────────────────────────────────────────────────────────────────────────┘
```

**读图要点**：分派点在 `AgentEngine` 内部，两条执行路径**下方共汇**同一组标准能力。这才是 Harness 的实质——不是「同一种执行方式」，而是**同一套记账、观测、提示词、治理**。

---

## 四、双形态 Agent 模型

### 4.1 两种形态

| 维度 | `FLOW`（现有） | `CONVERSATIONAL`（新增） |
|---|---|---|
| 执行核 | `StateMachineOrchestrator` / `DagOrchestrator` | `ToolCallingService` 单 ReAct 回合 |
| 图定义 | 引用 `flowCode` + `flowVersion` | **无**，`flow_code` 可空 |
| `graph_snapshot` | 有（版本锁定） | 无（单步实例） |
| 入参校验 | `StartInputValidator` 强校验 | 不校验（自由对话） |
| 历史 messages | 不支持（节点配置驱动） | **原样注入**（运行时入参） |
| 挂起/续跑 | 支持 | 不支持（单回合无中断点） |
| 典型用例 | 博客系列生成、编排型业务 | Flow Copilot、通用对话 |

### 4.2 门面扩容（并列新增，不改现有签名）

```java
// 新增：对话形态入口
public OrchestrationContext converse(AgentDefinition definition,
                                     List<Map<String, Object>> messages,
                                     Map<String, Object> inputs,
                                     String userId, String conversationId,
                                     AgentEventSink sink);

// 现有 run / signal / resume 各加一个带 sink 的重载；
// 旧签名保留并转发 sink = AgentEventSink.NOOP —— 所有既有调用方零改动。
```

分派规则：`run()` 遇到 `agentShape=CONVERSATIONAL` 抛明确异常提示改调 `converse()`，反之亦然（见 R6）。

### 4.3 为什么不把 Copilot 压成 1 节点 flow

这是本设计**最重要的一个否决**，理由按严重性递增：

| # | 代价 | 依据 |
|---|---|---|
| 1 | **写放大**：每轮对话建一条实例 + 存一份全图快照，10 轮 = 10 实例 + 10 快照 | C5 |
| 2 | **入参校验多余**：自由对话的 prompt 走 JSON Schema 强校验纯属负担 | C5 |
| 3 | **历史 messages 无处安放**：节点配置无法承载运行时消息列表 | C6 |
| 4 | **自举与版本锁定冲突**：`generate_flow` 写 `ai_flow` 表，Copilot 自身若是 flow 的一行，则「flow 实例在改 flow 表」 | C7 |

> **关键认知**：追求「所有 Agent 都是一张图」是**架构洁癖**，不是架构统一。真正需要统一的是**记账、观测、提示词、工具治理**——双形态在这四件事上是完全统一的。原 [对话式流程生成与Agent派生设计.md](./对话式流程生成与Agent派生设计.md) 第〇章「不是一个 ai_flow 自举」的判断是正确的，本设计**维持该判断**。

---

## 五、事件与流式能力

### 5.1 事件模型（新包 `com.nebula.common.ai.event`）

```java
public record AgentEvent(String type, String instanceId, String agentCode,
                         String nodeCode, int seq, long timestamp,
                         Map<String, Object> data) {}

public interface AgentEventSink {
    void emit(AgentEvent event);
    /** 传输已断开时返回 true，供执行侧提前止损 */
    default boolean isTerminated() { return false; }
    AgentEventSink NOOP = event -> { };
}
```

**为什么用 record + 字符串 type + Map data，而非接口继承体系**：事件要跨线程传递、序列化成 SSE、被 webhook 复用。这是最省的形态，且与既有的 `TransitionRecord` / `WebhookDelivery` 风格一致。

事件类型（`AgentEventTypes` 常量类）：

| 类型 | 触发点 | data 关键字段 |
|---|---|---|
| `instance.started` | 实例创建后 | `inputs` |
| `node.started` | 节点开始执行（**新增回调**） | `nodeCode`, `nodeType`, `attempt` |
| `node.succeeded` | 节点产物已 apply | `contextDelta` |
| `node.retry` | 节点失败待重试 | `attempt`, `errorSummary` |
| `transition` | 状态推进 | `fromState`, `toState` |
| `tool.started` | 工具执行前 | `toolCode`, `arguments` |
| `tool.finished` | 工具执行后 | `toolCode`, `success`, `latencyMs`, `resultBrief` |
| `token.delta` | 模型增量文本 | `content` |
| `instance.suspended` | 挂起 | `suspendedState`, `awaitingEvents` |
| `instance.done` | 终态成功 | `output` |
| `instance.failed` | 终态失败 | `error` |

### 5.2 事件穿透四层

**(1) `TransitionListener` → 事件：用组合，不改语义。**

`TransitionListener` 是**落库 SPI**，不应被观测污染。新增 `EventEmittingTransitionListener implements TransitionListener` 把回调翻译成事件，由 `AgentEngine` 组合装配：

```java
TransitionListener listener = TransitionListener.compose(
        instanceStore == null ? TransitionListener.NOOP
                              : new StoreBackedTransitionListener(instanceStore),
        new EventEmittingTransitionListener(sink, agentCode));
```

需给 `TransitionListener` 加 `static compose(TransitionListener...)`，逐个转发；**任一实现抛异常只 warn 并继续**——记账绝不能被观测拖垮（见 R7）。

**(2) 补 `onStateStarted`：本设计对内核的唯一侵入，且是加法。**

```java
default void onStateStarted(String instanceId, String stateCode, int seq, int attempt) { }
```

调用点：`StateMachineOrchestrator.executeState` 的重试 `while` 循环体开头，**在 `runWithOptionalTimeout` 之前**。

- 给 `default` 空实现 ⇒ 现有所有实现零改动。
- `StoreBackedTransitionListener` **不覆盖它** ⇒ 不落库，避免写放大。它纯粹服务于观测。

**(3) `ToolCallingService` → 事件**：见第六章。

**(4) `token.delta`：必须诚实分级。**

受 C1 约束，能力矩阵如下：

| 能力 | `FLOW` | `CONVERSATIONAL` | 批次 8 后 |
|---|---|---|---|
| `node.*` / `transition` 进度 | ✅ 完整 | N/A（无节点） | 不变 |
| `tool.*` 进度 | ✅ | ✅ | 不变 |
| `token.delta`（**中间轮**，带工具） | ❌ | ❌ | ✅ |
| `token.delta`（**终止轮**） | ✅（节点声明 `stream`） | ✅ | 不变 |

终止轮两种模式，由 `stream_config.finalTurnMode` 声明：

- **`CHUNKED`（默认）**：已聚合的 content 按窗口切片发 `token.delta`。无额外请求，行为等价于 Copilot 现状。
- **`STREAM`**：同一 messages（`tools` 置空、`toolChoice=none`）走 `aiService.stream` 发真增量。多一次请求，换真流式。

> **Copilot 现有的 B1/B2 混合流式架构是正确的**，本设计**吸收它为标准能力**而非消灭它——把私有常量 `streamFinalTurn` 变成每个 Agent 可声明的开关。

### 5.3 SSE 传输实现的落点

`AgentEventSink` 是 SPI，SSE 是传输实现。通用 `SseAgentEventSink` **不进 kernel**（会引入 `spring-webmvc` 依赖，破坏纯内核），放 starters 或 manager 共用包——符合「SPI 在内核、实现在卫星模块」的架构律。

### 5.4 前端契约不破：Copilot 事件翻译表

桥接器 `CopilotSseAdapter implements AgentEventSink` 留在 manager 侧，**前端零改动**：

| `AgentEvent.type` | → 现有 Copilot SSE 事件 |
|---|---|
| `token.delta` | `delta { content }` |
| `tool.started` | `tool_call { status:"start", name, arguments }` |
| `tool.finished` | `tool_call { status:"done", name, success, resultBrief }` |
| `instance.done` | `done` |
| `instance.failed` | `error { message }` |
| `tool.finished` 且 `toolCode ∈ {generate_flow, derive_agent}` 且成功 | 额外发 `flow` / `agent`（**业务语义钩子，留在 manager**） |
| `node.*` / `transition` | 丢弃（对话形态本就不产生） |

### 5.5 `DefaultAiService.stream` 的治理缺口

受 C4，`stream` 与 `chat` 治理不对称。这是 `AiService` 自身缺陷，**作为独立前置批次 0 修复**，不混进 Harness：给 `stream` 包上与 `chat` 同构的骨架（`publishBefore` → 过滤器链 → `persistConversation` → `publishAfter` / `writeLog`），配 `AggregatingCallback` 在透传 delta 的同时累积全文。

> **必须明写的降级语义（R2）**：过滤器链契约是 `Map doFilter(AiRequest)` 同步返回。流式套进去后，**过滤器拿到的是流结束后的聚合结果，无法中途改写增量**。这是可接受降级——过滤器的现实用途（脱敏、限流、审计）都在请求侧或聚合侧生效。

---

## 六、工具治理收口

### 6.1 全局开关 → 全局缺省 + per-agent 覆盖（解 C2）

```java
public record ToolCallingOptions(Boolean enabled,        // null=跟随全局；TRUE=强制启用
                                 Integer maxIterations,  // null=跟随全局
                                 Integer toolTimeoutMs,
                                 String finalTurnMode) {
    public static ToolCallingOptions fromGlobal() { ... }
}

public interface ToolCallingService {
    /** 旧签名保留为 default 转发 —— AgentReactNodeExecutor 零改动 */
    default Map<String, Object> run(AiRequest r, Collection<String> codes, ToolContext ctx) {
        return run(r, codes, ctx, ToolCallingOptions.fromGlobal(), AgentEventSink.NOOP);
    }

    Map<String, Object> run(AiRequest request, Collection<String> allowedToolCodes,
                            ToolContext toolContext, ToolCallingOptions options, AgentEventSink sink);
}
```

`resolveWhitelist` 首行改为：

```java
boolean effective = options.enabled() != null ? options.enabled() : config.isEnabled();
```

> **这是一次主动的安全权衡，必须写进决策记录（R3）**：全局开关从「总闸」降级为「缺省值」，确实弱化了一道安全阀。代价由白名单承担——**而白名单本来就是真正的把关者**（允许集合 ∩ 注册表存在，且 `executeOne` 内做二次校验防模型臆造工具名）。全局 boolean 从来不是安全边界，只是「没配好别乱跑」的保险丝。**Copilot 事实上已经绕过了它**；与其让绕过散落在业务模块，不如在 SDK 里给出正规的 per-agent 开关，再用 `budget_config` 补上真正的成本闸门。

### 6.2 注入 sink（解 Copilot 第二个理由）

- `executeOne` 前后发 `tool.started` / `tool.finished`；
- 终止轮按 `finalTurnMode` 发 `token.delta`；
- **所有发射全部 try/catch 只 warn**，写法照现有 `audit` 方法——绝不影响主流程。

这两条改完，`FlowCopilotService` 中约 400 行可整段删除：`resolveWhitelist` / 超时线程池 / 异常隔离 / `audit` / `buildToolSchemas` / `parseArguments` / `stringifyResult` / `toolMessage` / `appendAssistantMessage` / 终止轮切片。

**迁移后 Copilot 合法保留的部分**：工具产物 → `flow` / `agent` 事件的业务映射、few-shot RAG 召回接入、`AgentDefinition` 与 messages 装配。目标 **< 150 行**。

### 6.3 `UserContext` 跨线程传播上收为标准能力

Copilot 的已知风险 R1（SSE 线程丢失登录态）**迁移后不会自动消失**——`generate_flow` 内部调 `FlowAdminService.save` 依赖 `UserContext.getUserId()` 填 `createBy`。Harness 把执行搬到哪个线程，问题就跟到哪。

方案：`DefaultToolCallingService.invokeWithTimeout` 在提交任务前对当前线程 `UserContext` 做快照传入工具线程，`finally` 清理。**这条现在散在 Copilot 里，应上收为 Harness 标准能力**——否则每个用工具写库的 Agent 都要重踩一遍。归批次 3。

---

## 七、提示词治理

### 7.1 解析链（新包 `com.nebula.common.ai.prompt`）

```java
public interface PromptRepository { PromptTemplate find(String promptCode); }
public record PromptTemplate(String promptCode, String role, String content, int version) {}
```

- `InMemoryPromptRepository` —— 内核默认，降级用；
- `DatabasePromptRepository` —— `nebula-sdk-ai-flow` 提供，读 `ai_prompt` 表。

优先级链，**与模型档案三层链同构**（具体压抽象，好记）：

```text
节点内联 systemPrompt / promptTemplate（非空）
  > 节点 nodeConfig.promptCode / systemPromptCode → PromptRepository
    > Agent 级 system_prompt_code → PromptRepository
      > 空
```

**内联优先、promptCode 兜底**——保证现有所有 flow **零行为变化**。

### 7.2 先消除重复装配（解 C8）

抽内核类 `AiRequestAssembler`，承担：模型档案三层合并 + 提示词解析链 + 变量池装配。`PromptNodeExecutor` 与 `AgentReactNodeExecutor` 改为委托。

**纯重构、零行为变化**，但它是「提示词治理只写一遍」的前提，必须先做（批次 1）。

### 7.3 版本化：本期不加，但有一条铁律

`ai_prompt` 现无 version 列，**本期不加**——版本锁定已由 `graph_snapshot` 承担，重复造轮子。

> **铁律（必须实现）**：`FLOW` 形态在 `serializeSnapshot` **之前**，把 promptCode **解析成实际文本回填进 `FlowDefinition` 节点再序列化**。
>
> 这样快照里存的是**已解析的最终提示词**，崩溃恢复/重放**绝不会**因为有人改了 `ai_prompt` 而漂移——与四铁律中「重放只恢复产物、不重新调模型」一脉相承。
>
> `CONVERSATIONAL` 形态无快照，每次取最新，符合直觉。

### 7.4 `CopilotSystemPrompt` 迁移

63 行 text block → 一行 `ai_prompt`（`prompt_code = 'FLOW_COPILOT_SYSTEM'`, `role = 'system'`）。

Java 常量**保留为兜底**：`find` 返回 null 时降级使用，标 `@Deprecated` 并注明「权威源是 `ai_prompt`，本常量仅为首次部署的种子」。符合「实现缺失时优雅降级」的架构律。

---

## 八、能力声明模型（DDL）

**原则：能声明的都进 `ai_agent` 表，不再散落在 Java 常量和 nodeConfig 里。**

```sql
ALTER TABLE `ai_agent`
  ADD COLUMN `agent_shape` varchar(16) NOT NULL DEFAULT 'FLOW'
      COMMENT 'Agent 形态：FLOW=编排图驱动 | CONVERSATIONAL=单 ReAct 回合对话',
  ADD COLUMN `tool_config` json NULL
      COMMENT '工具声明：{"enabled":true,"toolCodes":[...],"maxIterations":8,"toolTimeoutMs":15000}；enabled 缺省跟随全局',
  ADD COLUMN `stream_config` json NULL
      COMMENT '流式声明：{"enabled":true,"finalTurnMode":"CHUNKED","chunkSize":24}',
  ADD COLUMN `system_prompt_code` varchar(64) NULL
      COMMENT 'Agent 级系统提示词编码，引用 ai_prompt.prompt_code；节点内联优先',
  ADD COLUMN `rag_config` json NULL
      COMMENT 'RAG 声明：{"fewShot":{"enabled":true,"topK":3,"minScore":0.6}}',
  ADD COLUMN `budget_config` json NULL
      COMMENT '预算护栏：{"maxTokens":100000,"maxToolCalls":30,"maxWallClockMs":600000}';

-- CONVERSATIONAL 形态无编排图，需放开 NOT NULL
ALTER TABLE `ai_agent` MODIFY COLUMN `flow_code` varchar(64) NULL
      COMMENT '引用的编排图编码；agent_shape=CONVERSATIONAL 时为空';
```

`AgentDefinition` 增加同名 `String` 字段（存 JSON 原文，与既有 `memoryConfig` 风格一致），配三个解析类 `AgentToolPolicy` / `AgentStreamPolicy` / `AgentBudgetPolicy`，**照 `AgentMemoryPolicy` 写法**：解析失败降级为默认值并只 warn，绝不阻断。

**为什么用 json 列而非拍平成多列**：`memory_config` 已开此先例；这些配置会持续演进，json 列免于反复 ALTER 大表。

> `budget_config` 是本设计**唯一主动新增的治理能力**。理由直白：一旦按 6.1 打开 per-agent 工具开关，全局保险丝就没了，**需要真正的成本闸门顶上**。实现可放批次 4 之后，但 DDL 一次到位，避免二次改表。

---

## 九、编译期护栏

### 9.1 禁什么 / 放什么

**禁**（按**全限定类名**逐个点名）：

- `com.nebula.common.ai.api.AiService`
- `com.nebula.common.ai.agent.tool.ToolCallingService`
- `com.nebula.common.ai.flow.FlowEngine`
- `com.nebula.common.ai.orchestration.*Orchestrator`

**放**（必须显式列出，否则误伤）：

| 放行项 | 理由 |
|---|---|
| `AgentEngine` | **唯一合法入口** |
| `AgentDefinition` / `OrchestrationContext` / `AgentEvent` / `ContextKeys` | 入口的参数与返回值 |
| `ToolDefinition` / `ToolContext` / `ToolRegistry` | 业务定义工具是**被 Harness 回调**，方向相反，不是绕过 |
| 各 store SPI 与 `ai_*` 实体 | **CRUD `ai_*` 表完全合法**，禁的是调运行时不是禁持久化 |
| `AiRequest` / `AiCallback` | 纯 DTO |

### 9.2 规则形态

```java
@AnalyzeClasses(packages = {"com.nebula.manager", "com.nebula.api", "com.nebula.blog"})
class HarnessBoundaryTest {

    @ArchTest
    static final ArchRule 业务代码不得直接调用AI运行时 =
        noClasses().that().areNotAnnotatedWith(HarnessBypass.class)
            .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.nebula.common.ai.api.AiService")
            .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.nebula.common.ai.agent.tool.ToolCallingService")
            .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.nebula.common.ai.flow.FlowEngine")
            .because("所有 Agent 执行必须经 AgentEngine（Harness）统一入口；"
                   + "确需绕过请加 @HarnessBypass 并写明理由与期限");

    @ArchTest
    static final ArchRule 控制器不得直接持有Harness =
        noClasses().that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.nebula.common.ai.agent.AgentEngine")
            .because("Controller 只做传输编解码，Agent 调用须经 Service 层");
}
```

> **第二条规则很重要**：它堵死 `AiChatAdminController` 现在「Controller 直连运行时」的病根。迁移后若 Controller 又直连 `AgentEngine`，等于换个姿势犯同样的错。

### 9.3 逃生舱

内核提供类级注解：

```java
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface HarnessBypass {
    String reason();          // 强制非空
    String until() default ""; // 期限，如 "2026-Q4"
}
```

`reason` 必须非空。**`grep @HarnessBypass` 就是欠账清单**——比「注释写一句」强，比「完全不给口子」现实。

### 9.4 模块落点与为什么不用 maven-enforcer

新建 `nebula-sdk/nebula-sdk-ai-archtest`（不叫 `-test`，后者听起来像测试工具库）：

- packaging `jar`，代码全在 `src/test/java`，`src/main` 为空；
- 依赖 `archunit-junit5` + 所有被检模块（test scope）；
- 在聚合 pom 中置于**最后**，reactor 顺序保证被检模块已编译。

**为什么不用 maven-enforcer**：enforcer 的粒度是 **artifact**，而 `nebula-service-manager` **必须**依赖 `nebula-sdk-ai`（要用 `AgentEngine`）。artifact 粒度无法区分「用 `AgentEngine` 可以、用 `AiService` 不行」。**必须类粒度 ⇒ 只能 ArchUnit。**

---

## 十、配置项汇总

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `nebula.ai.tool-calling.enabled` | `false` | **语义变更**：从「总闸」降级为「per-agent 未声明时的缺省值」 |
| `nebula.ai.tool-calling.final-turn-mode` | `CHUNKED` | 终止轮流式模式：`CHUNKED` \| `STREAM`；被 `stream_config` 覆盖 |
| `nebula.ai.tool-calling.chunk-size` | `24` | `CHUNKED` 模式下切片字符数 |
| `nebula.ai.event.enabled` | `true` | 事件发射总开关；关闭后 sink 一律 NOOP |
| `nebula.ai.event.include-context-delta` | `false` | `node.succeeded` 是否携带产物增量（含敏感数据风险，默认关） |
| `nebula.ai.prompt.enabled` | `true` | 是否启用 `ai_prompt` 解析链；关闭则仅用内联提示词 |
| `nebula.ai.harness.max-agent-depth` | `8` | 沿用现有子 Agent 递归深度上限 |
| `nebula.ai.harness.default-shape` | `FLOW` | `agent_shape` 为空时的兜底形态 |

---

## 十一、风险与取舍

| | 风险 | 对策 |
|---|---|---|
| **R1** | **ArchUnit 规则写宽会炸掉整个 manager**——`ToolDefinition` / `ToolContext` / store SPI / DTO 全在 `com.nebula.common.ai.**` 下，一条 `resideInAPackage("com.nebula.common.ai..")` 会让批次 7 整个卡住 | **规则必须按全限定类名黑名单点名，严禁包路径白名单**。这是本设计最容易写错的一处，落地时须逐条对照 9.1 放行表 |
| **R2** | `stream` 套过滤器链后，过滤器只能拿到聚合结果，**无法中途改写增量** | 文档明写降级语义；过滤器的现实用途（脱敏/限流/审计）都在请求侧或聚合侧，可接受 |
| **R3** | 全局工具开关降级为缺省值，**弱化一道安全阀** | 白名单双重校验（允许集合 ∩ 注册表 + `executeOne` 二次校验）+ `budget_config` 成本闸门顶上 |
| **R4** | 删 `Agent` / `AbstractAgent` / `AgentRegistry` 可能有未知外部引用，导致启动失败 | 已全仓 grep 确认零引用；**删除前再次全仓 grep 复核**，含 XML/配置文件 |
| **R5** | 批次 8 累积 `delta.tool_calls` 分片，**各厂商分片粒度与 index 语义差异大** | 批次 8 独立可选、不阻塞关键路径；保留同步回退；按 `index` 归并并对畸形分片容错跳过 |
| **R6** | 双形态引入分派歧义，调用方传错形态 | shape 不匹配时抛**明确异常并提示应改调哪个方法**，不做静默兼容 |
| **R7** | 事件穿透可能拖垮主流程（sink 慢、抛异常、传输断开） | 所有发射 try/catch 只 warn；`compose` 中任一 listener 异常不影响其余；**sink 异常绝不影响实例终态**（批次 2 验收硬指标） |
| **R8** | 批次跨度长，中间态可能出现「新旧两套并存」的混乱 | 所有 SDK 变更均为**加法**（新签名 + default 转发 / 新回调 + default 空实现），旧调用方零改动；护栏最后合入 |
| **R9** | `ai_agent` 放开 `flow_code` NOT NULL 后，FLOW 形态误存空值 | `AgentEngine` 分派时校验：`agentShape=FLOW` 且 `flowCode` 为空 → 抛明确异常；管理端保存时同步校验 |

---

## 十二、落地批次

> **顺序铁律：SDK 能力先落，Copilot 才能迁；护栏最后合，否则中间态自己红。**

| 阶段 | 交付 | 验收 |
|---|---|---|
| **0** `AiService.stream` 补齐 | `stream` 包 `publishBefore` / 过滤器链 / `persistConversation` / `writeLog`；`AggregatingCallback` | 单测：stream 一次后 `AiCallLogger.log` 调用 1 次、会话表 +2 条、过滤器调用 1 次；`/admin/ai-chat/stream` 手工回归行为不变 |
| **1** 请求装配去重 + 提示词链 | `AiRequestAssembler`；`PromptRepository` SPI + `InMemoryPromptRepository`；`DatabasePromptRepository`；两个 executor 改为委托 | 现有 flow 单测**零改动**通过（纯重构）；新增：节点配 `promptCode` 能取到文本；内联优先于 `promptCode` |
| **2** 事件 SPI 与内核穿透 | `AgentEvent` / `AgentEventSink` / `AgentEventTypes`；`TransitionListener.onStateStarted` + `compose`；`EventEmittingTransitionListener`；`run(..., sink)` 重载 | 跑 3 节点 flow，sink 按序收到 `instance.started → node.started → node.succeeded → transition → … → instance.done`，`seq` 单调递增；**sink 抛异常不影响实例终态** |
| **3** `ToolCallingService` 扩容 | `ToolCallingOptions`；新签名（旧签名 default 转发）；per-agent `enabled`；sink 注入；`finalTurnMode`；`UserContext` 跨线程传播 | 全局 `enabled=false` + `options.enabled=TRUE` 时工具**确实被调用**；发出 `tool.started`/`tool.finished`；`CHUNKED` 发出 `token.delta`；`AgentReactNodeExecutor` **零改动**仍通过 |
| **4** Harness 双形态 | `ai_agent` DDL；`AgentDefinition` 新字段 + 三个 Policy；`converse()`；shape 分派；**promptCode 在快照前解析回填** | `CONVERSATIONAL` Agent 跑通 ReAct 并落一条实例；FLOW 的 `graph_snapshot` 内是**已解析的提示词文本**（改 `ai_prompt` 后 resume 不漂移） |
| **5** Copilot 迁移 | 提示词落 `ai_prompt`；建 `flow_copilot` agent 记录；重写 Service；`CopilotSseAdapter`；删自建线程池与照抄的治理代码；`CopilotSystemPrompt` 标 `@Deprecated` 保留兜底 | **前端零改动**：6 种 SSE 事件名与 payload 字段逐一比对一致；端到端跑通「生成流程 + 派生 Agent」；`FlowCopilotService` **< 150 行** |
| **6** AiChat 迁移 | 建 `general_chat` agent（无工具）；新建 Service 层；Controller 只留传输编解码；两端点各用一个 sink 实现 | 两端点对外行为不变；对话现在**有**会话落库与调用日志（批次 0 的收益在此兑现） |
| **7** 删死代码 + 合护栏 | 删 `Agent` / `AbstractAgent` / `AgentRegistry` + autoconfig bean；建 `nebula-sdk-ai-archtest` + `@HarnessBypass` + 两条规则 | `mvn clean test` 全绿；**故意**在 manager 加一个 `@Autowired AiService`，构建**必须失败**并打印 `because` 文案 |
| **8** Provider 真流式 | `OpenAiChatProvider.readStream` / `extractDelta` 按 `index` 累积 `delta.tool_calls` 分片，聚合出 `toolCalls` + `assistantMessage` | 带工具的**中间轮**真流式；5.2 能力矩阵中间轮转 ✅；**同步路径行为完全不变** |

**并行关系**：0 ‖ 1 可并行；**2 → 3 → 4 严格串行**；5 ‖ 6 可并行；7 收尾；8 独立，可延后但本轮已确认要做。

---

## 十三、端到端验证

### 批次 0
```sql
-- stream 一次后核对（此前这三处均为空）
SELECT COUNT(*) FROM ai_call_log       WHERE conversation_id = '<cid>'; -- 期望 ≥1
SELECT role, content FROM ai_conversation_message WHERE conversation_id = '<cid>'; -- 期望 user + assistant 各 1 条
```

### 批次 2
挂一个打印型 sink 跑 3 节点 flow，断言事件序列与 `seq` 单调：
```text
instance.started(seq=0) → node.started(node_1) → node.succeeded(node_1)
→ transition(node_1→node_2) → node.started(node_2) → … → instance.done
```
再挂一个**必抛异常**的 sink 重跑，断言 `ai_agent_instance.status = 'SUCCESS'`（观测异常不影响终态）。

### 批次 3
```yaml
nebula.ai.tool-calling.enabled: false   # 全局关
```
Agent 声明 `tool_config: {"enabled": true, "toolCodes": ["echo"]}`，跑通后断言 `echo` **确实被调用**且收到 `tool.started` / `tool.finished`。

### 批次 4
```sql
-- 1. 跑一个带 promptCode 的 FLOW agent，然后篡改提示词
UPDATE ai_prompt SET content = '被改过的提示词' WHERE prompt_code = '<code>';
-- 2. resume 该实例，断言 graph_snapshot 内仍是旧文本、行为不漂移
SELECT graph_snapshot FROM ai_agent_instance WHERE instance_id = '<id>';
```

### 批次 5
用浏览器 DevTools 抓 `/admin/ai-flow/copilot/stream` 的 SSE 帧，与迁移前录制的帧**逐条比对**事件名与 payload 字段名。要求完全一致。

### 批次 7
```bash
# 反向验证：故意违规必须构建失败
# 在 nebula-service-manager 任意 Service 加 @Autowired AiService aiService;
mvn clean test    # 期望 HarnessBoundaryTest 失败，并打印 because 文案
```

### 批次 8
对同一个带工具的对话分别走批次 8 前后两版，断言：中间轮出现 `token.delta`，且**最终 `toolCalls` 内容与同步路径逐字段一致**。

---

## 十四、决策记录

### 已定（本轮 · 需求评审）

1. **Harness 定义** = 内部统一的 Agent 运行外壳（自研），非 Claude Code 规范、非 Harness.io 平台。
2. **强制力** = 硬收口：唯一入口 + 编译期阻断。业务侧禁止直调 `AiService` / `ToolCallingService`。
3. **首要能力** = 流式与进度可观测，须成为所有 Agent 的标准能力，而非 Copilot 私有。
4. **Agent 形态** = 双形态 `FLOW` + `CONVERSATIONAL`。
5. **Provider 真流式** = 做，作为批次 8，不阻塞关键路径。
6. **排期** = 完整八批次全走，不砍。
7. **本轮交付** = 设计文档 + 落地计划，不含实现代码。

### 已定（本轮 · 设计判断）

8. **不新建 `AgentHarness` 类**，`AgentEngine` 就地升格。两个门面并存必然产生权威归属歧义，且唯一外部调用方 `AiAgentAdminServiceImpl` 已在用它。
9. **不把 Copilot 压成 1 节点 flow**。四条代价见 4.3，其中 C7 自举与版本锁定冲突为致命项。维持 [对话式流程生成与Agent派生设计.md](./对话式流程生成与Agent派生设计.md) 第〇章的原判断。
10. **全局工具开关降级为缺省值**（6.1）。这是主动的安全权衡，代价由白名单双重校验 + `budget_config` 承担。
11. **删除 `Agent` / `AbstractAgent` / `AgentRegistry`**。零子类零调用，且其 `chat` / `chatWithTools` 正是要禁的行为，位于内核包内 ArchUnit 够不着——**留着它硬收口就是假的**。它想解决的「代码定义 Agent」在新模型下有更好答案：`agent_shape=CONVERSATIONAL` + 一行 `ai_agent` + 一组 `ToolDefinition` bean。**代码定义的应当是工具，不是 Agent 本身。**
12. **`ai_prompt` 本期不加 version 列**。版本锁定由 `graph_snapshot` 承担，代价是必须实现 7.3 的「快照前解析回填」铁律。
13. **护栏用 ArchUnit 不用 maven-enforcer**。后者粒度是 artifact，无法表达「用 `AgentEngine` 可以、用 `AiService` 不行」。
14. **`SseAgentEventSink` 不进内核**，避免 `spring-webmvc` 污染纯内核，遵循既有架构律。
15. **三个 SSE 线程池最终收敛为 2 个而非 1 个**：`ai-chat-sse` 与 `ai-copilot-sse` 在批次 5/6 自然合并；工具执行池由 `DefaultToolCallingService` 独占是**正确的**（需与模型调用线程隔离才能做超时兜底），不应合并。

### 待定（留后续轮次）

- `AgentMemoryPolicy.ExportStrategy.SUMMARY` 目前落到 `APPEND`，LLM 摘要未接。
- `AgentNodeExecutor` 子 Agent 挂起当前抛异常，「子挂起 → 父挂起 → 子完成唤醒父」的异步编织未做。
- MCP 运行时（`McpToolAdapter`）仍为设计态，接入后自动获得 Harness 全套治理。
- `budget_config` 的实际计费与熔断实现，DDL 本期到位，逻辑可延后。

---

## 关联文档

- [智能体设计.md](./智能体设计.md) —— `ai_flow` / `ai_agent` 三表结构、状态机内核语义、崩溃恢复四铁律。**本文档的上位文档，先读它。**
- [对话式流程生成与Agent派生设计.md](./对话式流程生成与Agent派生设计.md) —— Flow Copilot 现状、混合流式工具循环、SSE 事件契约、R1 登录态风险。**批次 5 迁移的对照基准。**
- [计划-执行节点设计.md](./计划-执行节点设计.md) —— `AgentReactNodeExecutor` 的定位与 ReAct 在三种时间尺度上的映射。
- [多智能体协作层设计.md](./多智能体协作层设计.md) —— Lead/Worker 协作层，未来将建在本文档的 Harness 之上。
- [跨实例迭代层设计.md](./跨实例迭代层设计.md) —— `IterationDriver` 与跨实例递推，位于 Harness 之上一层。
- [编排回调Webhook设计.md](./编排回调Webhook设计.md) —— 回调事件与本文档 `AgentEvent` 的关系（前者对外投递，后者对内观测）。
- [向量检索基建（Milvus）设计.md](./向量检索基建（Milvus）设计.md) —— `rag_config` 声明所依赖的四个 RAG 场景。

> **落地后须补**：在 [智能体设计.md](./智能体设计.md) 第十三章增补一行指向本文档，维持文档集的双向链接。