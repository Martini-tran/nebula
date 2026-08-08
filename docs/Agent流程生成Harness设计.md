# Agent 流程生成 Harness 设计

> 用 harness 理念重做「AI 生成 Agent 流程」：从「一次性吐整张图」改为「模型在可反馈的环境里迭代建图」。

## 一、为什么要重做

### 1.1 现状：一次性生成器

现有实现在 `nebula-service-manager` 的 `ai/copilot/` 包下，形态是**一把梭**：

```
用户需求 ──▶ list_node_types / list_tools（喂参考资料）
         ──▶ generate_flow（模型一次吐出整张 DAG 的 JSON）
         ──▶ 落库 ai_flow 三表
```

`GenerateFlowToolDefinition`（318 行）要求模型在**单次工具调用**里把节点、边、条件、参数全部想对，然后直接落库。

### 1.2 三个根本缺陷

**缺陷一：没有反馈闭环。**
`FlowCopilotService.loop`（第 166-185 行）确实是个工具循环，但回灌给模型的是**工具执行日志**，不是**流程运行结果**。模型永远不知道自己生成的流程能不能跑、跑出来对不对。

**缺陷二：校验太晚且太粗。**
`GenerateFlowToolDefinition.validate` 只有 4 条规则（nodeCode 重复、边引用不存在），且**只在落库那一刻做一次**。模型拿到 `{ok:false, error:"nodeCode 重复: X"}` 后，只能把整张图重吐一遍。

**缺陷三：产物不可见、不可改。**
落库之后没有读回工具。改一个节点 = 整张图重新生成。会话无状态，多轮对话改不动同一张图。

### 1.3 Harness 理念的解法

Harness 的本质是：**给模型一个能观察、能动作、能看到动作后果的环境**，让它自己收敛，而不是要求它一次想对。

```
一把梭：  想 ──▶ 做 ──▶ (结束，对错未知)
harness： 想 ──▶ 做一小步 ──▶ 看后果 ──▶ 修正 ──▶ 做下一步 ──▶ ... ──▶ 确认无误 ──▶ 提交
```

对应到本项目，就是把「生成流程」拆成细粒度动作，每个动作都有**结构化、字段级、即时**的反馈。

### 1.4 边界：Harness 替代现有流程生成器

本文的 Harness 是**流程生成期的主控外壳**，用来替代现有「模型一次吐整图 + `FlowCopilotService` 直接工具循环」的
生成路径。它负责迭代驱动、草稿状态、工具调度、字段反馈、模拟、确认与提交，而不只是挂在既有循环上的一组工具。

`AgentEngine` 仍可作为**已提交流程**的运行时执行内核；但 Flow Copilot 不以接入 `AgentEngine.converse(...)` 为前置条件。
生成 Harness 与运行时 Harness 是两个层次，后续可适配整合，不能因此削弱本方案对生成路径的替代。

---

## 二、设计决策（已确认）

| 决策点 | 选择 | 理由 |
|---|---|---|
| 形态 | **A：单 Agent 在 harness 里迭代建图** | 多 agent 分工是上层加法；没有可反馈环境，并行也只是并行地一把梭 |
| 位置 | **沉到 SDK** | 复用给 manager 之外的服务；与 `nebula-sdk-ai-flow` 同层 |
| 草稿态 | **DB 存稿（新增 `ai_flow_draft` 表）** | 内存方案的容量与泄漏顾虑；顺带解决重启丢失与多实例 |
| 草稿历史 | **只存当前态** | 无审计需求；一行一草稿，动作覆盖更新 |
| 试运行 | **模拟为主 + 真跑可选** | 默认零 token、零副作用；真跑作最终验收 |
| 旧 `generate_flow` | **下线** | 保留会让模型在两条路径间摇摆；选择困难的代价大于快速通道收益 |
| 引擎覆盖 | **DAG + STATE_MACHINE 都做** | 两套规则集并行设计，不是「DAG 为主 + 状态机补丁」 |

### 2.1 关于「草稿存 md 文件」的评估结论

曾考虑把草稿存为临时 md 文件（Claude Code 式的文件工作区）。**结论：不采用**，但其背后的需求成立且已被吸收。

**不采用的原因：**

1. **md 无 schema，承载 DAG 不可靠。** 节点有 12+ 字段，边有 SpEL 条件。要么写脆弱的 md→FlowDefinition 解析器，要么在 md 里嵌 JSON（那 md 只是无用的壳）。这一层解析是**新增的错误来源**，不产生价值。
2. **丢掉最值钱的即时校验。** 草稿是 `FlowDefinition` 对象时，`add_node` 能立刻返回「nodeType=PROMPT 必须提供 promptTemplate」这种字段级错误。草稿是 md 文本时，必须先解析成功才能校验，报错退化为「第 23 行格式不对」，模型难以自愈。
3. **每步重吐全文，错误率更高。** 原痛点是「一次吐整张图容易错」，md 方案让模型每步都重吐整个文件，是加重而非缓解。
4. **并发与多实例。** 临时文件的命名冲突、多实例间不可见。

**吸收的合理内核 —— 两个真需求，用「视图」而非「存储格式」满足：**

- **「我想看见模型在建什么」** → `render_draft` 把结构化草稿**渲染成** md/mermaid 推给前端与模型。渲染是单向的，无解析风险。
- **「模型要能看到全局再改」** → `read_draft` 先返回全图索引摘要；需要字段细节时按节点/边分页读取。
  这样模型仍能建立全局视图，又不会把完整图反复塞满上下文。

> 存储用结构化对象，视图渲染成 md。

### 2.2 关于 DB 存稿 vs 内存存稿

内存方案的真实风险不是「并发量撑爆」（1000 并发约 50–200MB，撑得住），而是**会话没有明确的结束信号**——用户关掉浏览器，草稿永远留在 map 里。这是时间累积型泄漏，与并发数无关。

要安全就必须补 TTL 过期清理 + 会话数上限 + 单草稿节点上限，**加完等于手写一个弱化版数据库，且重启仍丢**。

| | 内存+TTL+上限 | DB 草稿表 |
|---|---|---|
| 内存占用 | 受控但常驻 | 接近零 |
| 过期清理 | 自写定时器 | `DELETE WHERE updated_at < ?` |
| 重启存活 | 丢 | 存活 |
| 多实例 | 破（需粘性会话） | 天然 OK |
| 额外成本 | 三套治理代码 | 一张表 |

单次 `add_node` = 一条 UPDATE（几毫秒），相比模型生成的秒级耗时在噪音里。

### 2.3 双引擎：两套规则集，不是主从关系

`ai_flow.engine_type` 有两个取值，**图语义根本不同**。harness 的校验与模拟必须按 `engineType` 分派，而非「DAG 规则 + 状态机补丁」。

| 检查项 | `DAG` | `STATE_MACHINE` |
|---|---|---|
| 成环 | **ERROR**（`DagOrchestrator` 拓扑排序会抛异常） | **正常特性**（回跳/重试靠成环表达） |
| START / END 节点 | 各恰好一个 | **不使用**，改用 `stateType` |
| ENTRY 态 | 不适用 | **恰好一个**，缺失则无法起步 |
| TERMINAL 态 | 不适用 | **至少一个且无出边**，缺失则永远撞 `maxTransitions` |
| 分支表达 | `IF` 节点 + 出边 `conditionExpr` | 任意状态的出边 guard + `sortNo` 裁决顺序 |
| 终止保证 | 拓扑序天然终止 | **必须验证终态可达**（否则死循环） |
| 编译期校验 | `FlowGraphFactory`：含成环、LOOP 成员、执行器缺失等 | `FlowStateMachineFactory` + Builder：定义/执行器、边端点、ENTRY 唯一、TERMINAL 存在 |

#### 两个必须由 harness 堵住的坑

**坑一：`stateType` 拼错会被静默吞掉。**
`StateType.of()` 对无法识别的值**返回 `NORMAL`**（宽松策略，为兼容缺列的旧数据）：

```java
// StateType.of() —— 无法识别时不报错，静默降级
catch (IllegalArgumentException e) { return NORMAL; }
```

模型把 `stateType` 写成 `"TERMINAL_STATE"`、`"terminal "`（带空格已被 trim 兜住，但拼写错没有），编译**不会报错**，而是当成普通态——流程永远走不到终态，直到撞 `maxTransitions` 才以失败告终，且报错信息完全指不到根因。

> **对策**：`add_node` 对 `STATE_MACHINE` 草稿做 `stateType` **白名单严格校验**（只接受 `ENTRY`/`NORMAL`/`TERMINAL`），拼错立即 ERROR。这是编译层给不了的。

**坑二：状态机缺少业务语义校验。**
`FlowStateMachineFactory` 及其 Builder 会校验边端点、ENTRY 唯一和 TERMINAL 存在，但不严格校验未知
`stateType`、TERMINAL 出边、guard 覆盖及“是否至少有一个终态可达”。harness 第一层仍须补齐这些语义。

#### 对 `simulate` 的影响

DAG 的模拟验证**连通性**；状态机的模拟验证**能不能停下来**。状态机靠 guard 逐条求值决定走向，模拟时 guard 大多无法判定，一旦选错分支就可能在环里打转。

> 状态机模拟必须带**转移次数上限 + 访问路径环检测**，核心结论是「是否可达终态」，而非「哪些节点可达」。

---

## 三、模块结构

### 3.1 新模块：`nebula-sdk-ai-harness`

```
nebula-sdk/nebula-sdk-ai-harness/
└── src/main/java/com/nebula/common/ai/harness/
    ├── draft/
    │   ├── FlowDraft.java              草稿领域对象（持有 FlowDefinition）
    │   ├── DraftStore.java             SPI：草稿读写
    │   ├── DatabaseDraftStore.java     DB 实现（本模块内，使用 ai-flow Mapper）
    │   ├── DraftApplicationService.java 权限、预算、字段校验与 mutation 编排
    │   ├── DraftAccess.java            服务端访问上下文（用户/会话/权限，不由模型传入）
    │   ├── DraftNodeConverter.java     扁平工具字段 → FlowNodeDefinition/nodeConfig
    │   ├── DraftCommitter.java         SPI：提交到正式流程
    │   ├── DraftRunner.java            SPI：真实运行草稿
    │   ├── DraftConfirmationStore.java 服务端确认令牌 SPI
    │   ├── HarnessOperationStore.java  高风险操作幂等与结果查询 SPI
    │   └── DraftRenderer.java          草稿 → md/mermaid 视图
    ├── validate/
    │   ├── DraftValidator.java         校验入口：按 engineType 分派
    │   ├── ValidationIssue.java        结构化问题（字段级定位）
    │   ├── EngineRuleSet.java          SPI：一套引擎的规则集
    │   └── rules/
    │       ├── CommonRules.java        两引擎共用（nodeCode 唯一/边端点/模板变量）
    │       ├── DagRuleSet.java         START/END 唯一、成环 ERROR、孤儿节点
    │       └── StateMachineRuleSet.java ENTRY 唯一、TERMINAL 可达、stateType 白名单
    ├── simulate/
    │   ├── FlowSimulator.java          模拟入口：按 engineType 分派
    │   ├── DagSimulator.java           拓扑推进，验证连通性与数据流
    │   ├── StateMachineSimulator.java  转移推进，验证终态可达（带环检测）
    │   ├── SimulationExecutorRegistry.java 按 nodeType 生成替身执行器
    │   ├── SimulationLoopDriver.java   模拟专用 LOOP 驱动器
    │   └── SimulationReport.java       模拟报告
    ├── tool/                           harness 工具集（见第四章）
    │   ├── ListNodeTypesToolDefinition.java
    │   ├── CreateDraftToolDefinition.java
    │   ├── UpdateDraftMetadataToolDefinition.java
    │   ├── AddNodeToolDefinition.java
    │   ├── ConnectToolDefinition.java
    │   ├── DisconnectToolDefinition.java
    │   ├── UpdateNodeToolDefinition.java
    │   ├── RemoveNodeToolDefinition.java
    │   ├── ReadDraftToolDefinition.java
    │   ├── ValidateDraftToolDefinition.java
    │   ├── SimulateDraftToolDefinition.java
    │   ├── RealRunDraftToolDefinition.java
    │   ├── GetHarnessOperationToolDefinition.java
    │   └── CommitDraftToolDefinition.java
    ├── runtime/
    │   ├── FlowGenerationHarness.java  生成期主循环：观察→动作→反馈→收敛
    │   ├── HarnessToolScheduler.java   工具白名单、超时、审计与调用隔离
    │   └── HarnessEventSink.java       草稿/确认/产物事件
    ├── conversation/
    │   ├── HarnessRequest.java         SDK 请求模型（消息、模型参数、会话）
    │   ├── HarnessCallContext.java     已认证的调用上下文（用户、会话、权限、请求ID）
    │   ├── HarnessToolContext.java     跨工具线程传递的不可变调用上下文
    │   ├── HarnessPromptProvider.java  SPI：系统提示词与建图指令
    │   └── HarnessExampleProvider.java SPI：few-shot / 范式召回
    └── config/
        └── HarnessAutoConfiguration.java
```

**为什么是独立模块而非塞进 `nebula-sdk-ai-flow`：**
`nebula-sdk-ai-flow` 是**持久化模块**（store 包，各种 Mapper）。harness 是**生成期能力**，依赖 flow 的领域模型但不属于持久化职责。独立模块让不需要 AI 生成能力的服务不必引入。

### 3.2 依赖关系

```
nebula-sdk-ai-harness
   ├──▶ nebula-sdk-ai        (FlowDefinition/FlowEngine/ToolDefinition/FlowGraphFactory)
   └──▶ nebula-sdk-ai-flow   (草稿表实体与 Mapper；不反向依赖 harness)

nebula-service-manager
   └──▶ nebula-sdk-ai-harness  (DraftCommitter/DraftRunner、Prompt/Example Provider 的业务适配器；FlowCopilotService 调用 Harness)
```

`DatabaseDraftStore` 放在 harness 内部，因此只有 `harness → ai-flow` 一条 Maven 依赖；`ai-flow` 不实现
`DraftStore`，不会形成编译期环。`CommitDraftToolDefinition` 和 `RealRunDraftToolDefinition` 只依赖
`DraftCommitter` / `DraftRunner` SPI，manager 提供适配器并在适配器内调用 `FlowAdminService` / `FlowEngine`。
`ObjectProvider` 仅用于打破 Spring Bean 构造环，**不能**用来掩盖 SDK 反向依赖 manager 的问题。

草稿工具不得直接操作 Mapper，固定调用链为 `ToolDefinition → DraftApplicationService → DraftStore`：工具只负责
参数与反馈契约，应用服务负责访问控制、预算、双引擎字段校验和 revision 语义，Store 只负责原子持久化。mutation
API 按动作分别定义，不使用一个接收任意命令的通用 `mutate` 接口。

`FlowGenerationHarness` 不接收 manager 的 `CopilotStreamRequest`，也不引用 `CopilotSystemPrompt` 或
`FlowExampleService`。它只接收 SDK 的 `HarnessRequest` / `HarnessCallContext`；manager 将 HTTP DTO 转换为该请求，
并实现 `HarnessPromptProvider` / `HarnessExampleProvider`。这样保留现有系统提示词、few-shot 和前端模型参数，同时
维持 `harness → ai-flow` 的单向 Maven 依赖。

`HarnessToolScheduler` 在提交到工作线程前构造不可变 `HarnessToolContext`，工具从其中取得用户、会话与授权信息，
不得依赖 `UserContext` 等 ThreadLocal。这样超时隔离和异步 SSE 不会丢失草稿归属。

运行链路固定为：`FlowCopilotService（HTTP/SSE 适配）` → `FlowGenerationHarness` →
`HarnessToolScheduler` → `harness ToolDefinition` → `SPI 适配器`。Harness 取代旧 Copilot 直连工具循环并拥有白名单、超时、审计与迭代反馈；
`FlowCopilotService` 只保留传输适配和事件投影。

---

## 四、工具集设计

harness 的核心。每个工具对应一个细粒度动作，每个动作都返回**结构化反馈**。

### 4.1 全集

| 工具 | 职责 | 关键反馈 |
|---|---|---|
| `list_node_types` | 按引擎返回权威节点类型与角色约束 | DAG/STATE_MACHINE 分组描述；可按 engineType 过滤 |
| `create_draft` | 建空草稿（**须指定 engineType**，flowCode 可空），返回 draftId/revision | draftId、revision + 该引擎的建图约束提示 |
| `update_draft_metadata` | 修改名称、描述、目标 flowCode 等草稿元数据 | 新 revision + 变更字段 |
| `add_node` | 加一个节点 | 字段级校验错误 |
| `connect` | 连一条边（可带条件） | 端点存在性、条件语法 |
| `disconnect` | 精确断开错误边 | 被删除边；选择器不充分时返回歧义错误 |
| `update_node` | 改节点某几个字段 | 同 add_node |
| `remove_node` | 删节点（连带清理边） | 被清理的边列表 |
| `read_draft` | 读草稿摘要、指定节点或分页边 | 有界 canonical JSON + md/mermaid 渲染视图 |
| `validate_draft` | 全量校验 | 问题清单（分级） |
| `simulate_draft` | 模拟执行 | 执行轨迹 + 数据流 |
| `real_run_draft` | 真实执行（需服务端确认令牌） | 真实产物 |
| `get_harness_operation` | 查询超时或异步高风险操作 | `PENDING/RUNNING/SUCCEEDED/FAILED/UNKNOWN` + 安全结果摘要 |
| `commit_draft` | 原子提交草稿 | flowCode + version + committed revision |

### 4.2 反馈契约

所有工具返回统一结构：

```json
{
  "ok": false,
  "revision": 7,
  "issues": [
    {
      "level": "ERROR",
      "code": "MISSING_REQUIRED_FIELD",
      "nodeCode": "summarize",
      "field": "promptTemplate",
      "message": "nodeType=PROMPT 的节点必须提供 promptTemplate",
      "hint": "调用 update_node 补上该字段"
    }
  ],
  "draft": { "nodeCount": 5, "edgeCount": 4 }
}
```

**设计要点：**
- `ok:false` **不抛异常**（沿用现有约定），让模型看到错误后自愈
- `nodeCode` + `field` 给出**精确定位**，模型知道改哪
- `hint` 直接给出**下一步动作建议**，缩短收敛轮次
- `level` 分 `ERROR`（阻断提交）/ `WARN`（可提交但可疑）/ `INFO`
- 所有变更工具都接收 `expectedRevision`，返回新 revision；版本不一致返回 `DRAFT_CONFLICT`，模型须先
  `read_draft` 再重试。任何读写均从认证上下文取得用户和会话，不能把 `userId` / `sessionId` 作为模型可伪造的参数。
- 访问控制以 `userId` 为强边界，不提供管理员跨用户绕过：`draft.userId != current.userId` 一律拒绝；草稿
  `sessionId` 非空时当前会话还必须与其相等，草稿 `sessionId` 为空时同一用户可跨会话访问。
- `FlowDraft` 沿用 Lombok 可变 Bean，但只允许在 `DraftApplicationService` 的单次 mutation 内部修改。Store 每次
  返回独立对象；编译、校验和事件发布一律使用深拷贝，不得修改 canonical 草稿。
- mutation 固定为“加载独立快照 → 内存修改 → 字段校验/预算检查 → 整图 CAS”；校验失败或 CAS 失败均丢弃
  当前快照，`graph_json` 与 revision 不变。不得把“先查 revision、再普通 update”包装成乐观锁。
- `read_draft` 默认只返回全图摘要（节点编码、类型、输出键、边端点、当前 revision）；详细 canonical JSON 必须按
  `nodeCodes` 或 `edgePage` 分页请求。md/mermaid 同样受输出预算限制，只用于展示，不作为可回写的真相源。
- 单草稿强制限制节点数、边数、单模板长度、`nodeConfig` 大小和单次工具回灌字节数；超过预算返回
  `DRAFT_SIZE_LIMIT`，不截断 JSON 后继续伪装为完整结果。

#### `create_draft` 的引擎选择

`engineType` 是**建图第一决策**，选错则后续所有节点白建（DAG 用 START/END，状态机用 stateType，不可互换）。

`create_draft` 返回值中**直接附带该引擎的建图约束**，让模型建图前就知道规则：

```json
{
  "ok": true, "draftId": "d_x7k2", "engineType": "STATE_MACHINE",
  "constraints": [
    "本图用 stateType 标注节点角色，不使用 START/END 节点",
    "必须恰好一个 ENTRY 态作为起步",
    "必须至少一个 TERMINAL 态且无出边",
    "允许成环（回跳/重试），但须保证 TERMINAL 可达",
    "分支靠出边 conditionExpr(guard) + sortNo 裁决顺序，建议留一条无条件 default 边"
  ]
}
```

工具描述中同时给出**选型指引**：任务一次性向前推进用 `DAG`；需要回跳、重试、审批打回、多轮迭代收敛用 `STATE_MACHINE`。

`flowCode` 在创建草稿时允许为空，模型可先完成建图再命名；但提交前必须通过 `update_draft_metadata` 补齐。
`commit_draft` 不接收临时 flowCode 并顺带修改草稿，避免提交动作同时承担 mutation，导致已校验/模拟 revision 失真。

### 4.3 关键工具详解

#### `list_node_types`

`engineType` 为可选参数：传 `DAG` 或 `STATE_MACHINE` 时只返回对应引擎的权威说明；不传时按两个引擎分组返回，
供 `create_draft` 前选型。返回内容必须区分“执行节点类型”和“图语义角色”：

- DAG 返回可用执行节点类型以及 `START` / `END` / `IF` / `JOIN` / `LOOP` 的结构约束。
- STATE_MACHINE 返回可用执行节点类型以及 `stateType=ENTRY|NORMAL|TERMINAL` 的角色约束，不把 START/END
  当作状态机节点类型。
- `create_draft` 成功响应仍重复返回所选引擎约束，不能依赖模型记住前一次工具结果。

B1 上线本实现时替换 manager 中同编码的旧 `list_node_types` Bean，注册表内只能存在一个权威实现，不能用 Bean
排序掩盖重复工具编码。

#### `update_draft_metadata`

可修改字段限定为 `name`、`description`、`flowCode`、`defaultProfileCode`、`maxTransitions`。接口与
`update_node` 一样使用 `patch + clearFields`，且必须携带 `expectedRevision`。`engineType` 创建后不可修改；选错引擎
必须新建草稿，不能把已有节点原地转换到另一引擎。

#### `add_node`

入参对齐 `FlowNodeDefinition` 的扁平字段（沿用现有约定：扁平优先，直接可运行）。

按 `nodeType` 分派**类型专属校验**：

| nodeType | 必填校验 |
|---|---|
| `PROMPT` | `promptTemplate` 非空；`outputKey` 建议非空（WARN） |
| `TOOL` | `nodeConfig.toolCode` 非空 **且在 ToolRegistry 中存在** |
| `AGENT` | `nodeConfig.refAgentCode` 非空且已定义 |
| `AGENT_REACT` | `nodeConfig.toolCodes` 非空数组，每个都存在 |
| `LOOP` | `nodeConfig.members` 非空（编译期也会抓，此处提前） |
| `START`/`END` | 全图唯一性（**仅 DAG**；状态机不使用这两类节点） |
| `IF` | 出边需有 conditionExpr（连边时校验） |

`ToolDefinition` 新增 `invocationScopes(): Set<InvocationScope>`，其中 `InvocationScope` 为 `COPILOT_TOOL` /
`FLOW_NODE`。默认值仅为 `FLOW_NODE`（fail closed）；所有 Copilot/Harness 工具必须显式声明 `COPILOT_TOOL`。
`HarnessToolScheduler` 下发 schema 前和执行前各校验一次 `COPILOT_TOOL`，`ToolNodeExecutor` 执行前校验
`FLOW_NODE`，并按 `HarnessCallContext` 执行授权检查。这样生成出的流程无法间接调用 `commit_draft` /
`real_run_draft`。

工具调用参数在调度器中先做严格 JSON 解析与 JSON Schema 校验；解析失败或缺少必填字段返回结构化
`INVALID_TOOL_ARGUMENTS`，不能回退成空对象再执行。

**状态机草稿的额外字段校验**（`engineType=STATE_MACHINE` 时）：

| 字段 | 校验 |
|---|---|
| `stateType` | **白名单严格匹配** `ENTRY`/`NORMAL`/`TERMINAL`，拼错立即 ERROR（**不沿用 `StateType.of()` 的静默降级**） |
| ENTRY 唯一性 | 已存在 ENTRY 时再加 ENTRY → ERROR |
| TERMINAL 出边 | 给 TERMINAL 态连出边 → ERROR（在 `connect` 中校验） |

> **这是相对现有实现最大的增量**：现在只有 4 条通用规则，改为按类型 + 按引擎的字段级校验。

状态机相关工具参数保持扁平，由 `DraftNodeConverter` 写入运行时模型：

| 工具字段 | 写入位置 |
|---|---|
| `stateType` | `FlowNodeDefinition.stateType` |
| `maxAttempts` | `nodeConfig.stateConfig.retry.maxAttempts` |
| `backoffMs` | `nodeConfig.stateConfig.retry.backoffMs` |
| `retryOn` | `nodeConfig.stateConfig.retry.on` |
| `stateTimeoutMs` | `nodeConfig.stateConfig.timeoutMs` |
| `onError` | `nodeConfig.stateConfig.onError` |
| `errorState` | `nodeConfig.stateConfig.errorState` |
| `suspend` | `nodeConfig.stateConfig.suspend` |
| `awaitingEvents` | `nodeConfig.stateConfig.awaitingEvents` |

使用 `stateTimeoutMs` 而非 `timeoutMs`，避免与节点模型调用超时字段混淆。转换前严格校验枚举、数值范围和
`onError=GOTO_STATE` 时 `errorState` 必填；不能沿用运行时解析器的静默默认行为掩盖错误。

#### `update_node`

更新采用显式的 `patch + clearFields`，解决可变 Bean 无法区分“字段未传”和“显式传 null”的问题：

```json
{
  "draftId": "d_x7k2",
  "expectedRevision": 7,
  "nodeCode": "review",
  "patch": { "name": "人工审核", "stateType": "NORMAL" },
  "clearFields": ["promptTemplate", "profileCode"]
}
```

`patch` 只修改出现的字段，`clearFields` 只清空白名单内的可空字段；同一字段同时出现时返回
`INVALID_TOOL_ARGUMENTS`。`nodeCode` 不允许通过 update 改名，避免边端点级联和歧义；确需改名时删除并重建节点。

#### `disconnect`

现有 `FlowEdgeDefinition` 没有稳定 edgeId，因此用 `fromNode + toNode` 加可选的 `sortNo`、`conditionExpr`、
`eventName` 作选择器。若命中多条边则返回 `AMBIGUOUS_EDGE` 和候选摘要，模型必须先 `read_draft` 再精确重试；
不允许静默批量删除。边字段修改在 B1 采用“disconnect 后重新 connect”，暂不增加 `update_edge`。

#### `validate_draft`

**三层校验，逐层深入。第一层按 `engineType` 分派两套规则集。**

**第一层 —— 结构校验（自实现）**

*共用规则（`CommonRules`）*
- nodeCode 唯一性、边端点存在性（现有仅有的 4 条）
- 类型专属必填字段（4.3 表格）
- 模板变量同时识别 `{{var}}` 与 `#{var}`；先按 `inputMapping` 解析为上下文键，再检查初始输入与**支配当前
  节点的**上游产物。仅在某条分支可能缺失时给 WARN，不把「任意上游曾产出」误判为必然可用。

*DAG 专属（`DagRuleSet`）*
- START / END 节点各恰好一个
- 孤儿节点（无入无出）
- START 可达性、END 可达性

*状态机专属（`StateMachineRuleSet`）*
- **`stateType` 白名单严格校验**：只接受 `ENTRY`/`NORMAL`/`TERMINAL`，拼错立即 ERROR
  （堵住 `StateType.of()` 静默降级为 NORMAL 的坑，见 2.3 坑一）
- **ENTRY 恰好一个**：缺失则无法起步，多个则起步态不确定
- **TERMINAL 至少一个**：缺失则必然撞 `maxTransitions` 失败
- **TERMINAL 无出边**：终态有出边是语义矛盾
- **终态可达性**：从 ENTRY 出发，忽略 guard 做可达性搜索，若**任何 TERMINAL 都不可达** → ERROR
- **default 边覆盖**（WARN）：某状态的所有出边都带 guard 且无 default 边时，guard 全假会卡死

**第二层 —— 编译校验**

调对应工厂编译**草稿的深拷贝**，捕获 `OrchestrationException`。工厂会为未指定档案的节点补默认
`profileCode`，因此校验和模拟绝不能直接修改 canonical `graph_json`；草稿的任何持久化变更只能经 revision CAS 的
mutation 工具完成。

| engineType | 工厂 | 免费获得 |
|---|---|---|
| `DAG` | `FlowGraphFactory` | 执行器缺失、LOOP 成员为空、作用域引用不存在、**成环检测** |
| `STATE_MACHINE` | `FlowStateMachineFactory` / `StateMachineGraph.Builder` | 定义/执行器、边端点、ENTRY 唯一、至少一个 TERMINAL |

> ⚠️ **红利不对称**：状态机编译器已校验端点、ENTRY 与 TERMINAL，但不会严格识别未知
> `stateType`、终态出边、guard 覆盖和「可达 TERMINAL」的业务语义；这些仍须由第一层负责。状态机不做成环检测，
> 因为成环是正常特性。

**第三层 —— SpEL 条件语法校验**
复用 `ConditionCompiler` / `CondGroupCompiler` 编译边条件/guard，语法错误提前暴露。两引擎共用（状态机的 guard 就是 `ai_flow_edge.condition_expr` 编译出的谓词）。

#### `simulate_draft`

**零 token、零副作用**地验证连通性与数据流。

做法：`SimulationExecutorRegistry` 为每个 `nodeType` 注册同类型替身执行器；DAG 的 `LOOP` 另注入
`SimulationLoopDriver`，不能把一个 `MockNodeExecutor` 直接替换给生产 `FlowGraphFactory`。

| nodeType | 模拟行为 |
|---|---|
| `PROMPT` | 按 `outputMode` 产出假值：TEXT → `"[模拟输出:{nodeCode}]"`；JSON → 按需产出占位对象 |
| `TOOL` | 返回 `{"mock": true, "toolCode": "..."}` |
| `AGENT`/`AGENT_REACT` | 返回占位产物，不递归 |
| `IF` | 按边条件对模拟上下文求值；无法判定时做**有上限的分支探索**（含 default），并报告未覆盖分支 |
| `LOOP` | 按 `members` 走固定小轮次（如 2 轮），避免无限 |

**两种引擎的模拟目标不同：**

| | `DagSimulator` | `StateMachineSimulator` |
|---|---|---|
| 推进方式 | 拓扑序一遍过 | 逐次转移，可重复访问同一状态 |
| 核心结论 | **哪些节点可达**、数据流是否通 | **能否到达终态**、会不会死循环 |
| 终止条件 | 拓扑序走完 | 到达 TERMINAL / 撞模拟转移上限 / 检出死循环 |
| 特有产出 | `unreachableNodes` | `transitionPath`、`reachedTerminal`、`loopDetected` |

**状态机模拟的关键机制 —— 环检测。**
guard 大多无法在模拟期判定，选错分支就可能在环里打转。做法：
- 模拟转移上限取 `min(maxTransitions, 50)`，避免模拟本身跑太久
- 记录 `(状态, 上下文关键键集合)` 的访问指纹，**同一指纹重复出现 = 无进展死循环**，立即报 `SIMULATION_LOOP`
- guard 无法判定时按 `sortNo` 假设首条可满足，并记入 `undecidedGuards`；带有假设的轨迹只能说明“可能到达”，
  不能作为真实终止性的证明

未判定 guard 的 DAG 分支探索上限由配置控制（默认最大 16 条路径、每状态最多 4 条候选边）；超过上限返回
`SIMULATION_COVERAGE_TRUNCATED` WARN。模拟报告必须区分 `CONFIRMED` 与 `ASSUMED` 结论，不能把占位数据或
guard 假设包装成真实运行成功。

**模拟报告（DAG）：**
```json
{
  "engineType": "DAG",
  "reachedNodes": ["start", "fetch", "summarize", "end"],
  "unreachableNodes": ["orphan_node"],
  "contextKeys": {"raw": "...", "summary": "..."},
  "issues": [
    {"level":"ERROR","code":"UNRESOLVED_VAR","nodeCode":"summarize",
     "message":"模板变量 #{content} 在执行到该节点时上下文中不存在"}
  ]
}
```

**模拟报告（STATE_MACHINE）：**
```json
{
  "engineType": "STATE_MACHINE",
  "confidence": "ASSUMED",
  "transitionPath": ["draft", "review", "revise", "review", "publish"],
  "reachedTerminal": true,
  "terminalState": "publish",
  "transitionCount": 4,
  "undecidedGuards": [
    {"from":"review","chosen":"revise","reason":"guard 无法在模拟期判定，按 sortNo 取首条"}
  ],
  "issues": []
}
```

**两个模拟独有的价值：**

1. **`UNRESOLVED_VAR`（DAG）**：静态校验只能查「有没有节点产出这个 key」，模拟能查「**执行到这里时**它到底在不在上下文里」——节点顺序错了也能抓出来。
2. **`reachedTerminal:false`（状态机）**：静态可达性分析忽略 guard，模拟则按运行时的裁决顺序推进；在 guard
   无法判定时明确标记为假设。它能发现「结构上终态可达，但按 sortNo 优先路径仍走不出去」这类问题，但不能替代真跑。

#### `real_run_draft`

真实调 `DraftRunner.run`。第一次调用只创建 `REAL_RUN` 确认请求并发出 `confirm_required` 事件，**不执行**；前端
必须把用户确认回传给服务端，服务端签发一次性、短时、绑定 `draftId + revision + userId` 的令牌。只有携带未消费令牌的
第二次调用才能真实执行。草稿更新、令牌过期、用户或图版本不一致时令牌立即失效。

确认通过后，`DraftRunner` 必须先以 `draftId + revision + action` 创建或取得同一个 `HarnessOperation`，再消费令牌并
启动执行。超时或客户端断开只能把操作标为 `UNKNOWN`，返回 `operationId`；模型必须调用
`get_harness_operation` 查询，不得重复提交 `real_run_draft`。操作最终态和安全结果摘要持久化，确保同一 revision 的
真实运行至多启动一次。

当前 `FlowEngine.run` 只能按 `flowCode` 从正式 `FlowDefinitionRepository` 加载定义，不能直接运行未提交草稿。
因此 B4 要新增 `FlowEngine.runDefinition(FlowDefinition, ...)`（或等价的内部执行入口）：使用深拷贝定义、临时唯一
执行编码、禁用正式流程缓存与续跑持久化，且不写 `ai_flow` 三表。`DraftRunner` 的 manager 适配器只能调用该入口，
不能为试跑先落库再删除。

用于最终验收：模拟全绿之后跑一次真的，模型看到真实产物再决定是否提交。

#### `commit_draft`

提交前**强制**以最新 revision 重新执行 `validate_draft`（无 ERROR）。最终阶段默认要求
`lastSimulatedRevision == revision`；B2 在模拟器尚未交付时仅可通过显式临时配置关闭该门禁，B3 起默认开启。
默认 `CREATE_ONLY`：同 `flowCode` 已存在则
返回 `FLOW_CODE_CONFLICT`，绝不自动加后缀或静默覆盖。显式更新必须给出 `expectedFlowVersion`，并复用与真跑相同的
服务端确认机制；`DraftCommitter` 在一个事务内创建或取得幂等 `HarnessOperation`、做 revision CAS、正式流程条件写入
和草稿置 `COMMITTED`，避免校验后被并发覆盖、超时重试或重复提交。

---

## 五、数据模型

### 5.1 `ai_flow_draft` 表

```sql
CREATE TABLE `ai_flow_draft` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `draft_id`      varchar(64)  NOT NULL                COMMENT '草稿业务ID（工具入参用）',
  `session_id`    varchar(64)  DEFAULT NULL            COMMENT '归属会话（Copilot conversationId）',
  `user_id`       bigint       NOT NULL                COMMENT '归属用户（所有访问均须匹配）',
  `flow_code`     varchar(64)  DEFAULT NULL            COMMENT '目标流程编码（提交时用）',
  `name`          varchar(128) DEFAULT NULL            COMMENT '流程名称',
  `description`   varchar(512) DEFAULT NULL            COMMENT '流程描述',
  `engine_type`   varchar(32)  DEFAULT 'DAG'           COMMENT '执行内核 DAG|STATE_MACHINE',
  `graph_json`    longtext     NOT NULL                COMMENT '草稿图 JSON（FlowDefinition 序列化）',
  `revision`      bigint       NOT NULL DEFAULT 0       COMMENT '乐观锁版本，每次变更递增',
  `last_validated_revision` bigint DEFAULT NULL         COMMENT '最近一次无 ERROR 校验的草稿版本',
  `last_simulated_revision` bigint DEFAULT NULL         COMMENT '最近一次完成模拟的草稿版本',
  `status`        varchar(32)  NOT NULL DEFAULT 'BUILDING' COMMENT 'BUILDING|COMMITTED|ABANDONED',
  `committed_flow_code` varchar(64) DEFAULT NULL       COMMENT '提交后的流程编码',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draft_id` (`draft_id`),
  KEY `idx_user_session` (`user_id`, `session_id`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI流程生成草稿';
```

正式迁移脚本只新增此表，**不得**包含 `DROP TABLE`；已有库用版本化迁移管理，而不是每次部署重建。

真实运行与覆盖提交的确认单独保存，令牌数据库中只存 hash：

```sql
CREATE TABLE `ai_flow_draft_confirmation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `draft_id` varchar(64) NOT NULL,
  `revision` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `action` varchar(32) NOT NULL COMMENT 'REAL_RUN|OVERWRITE_COMMIT',
  `token_hash` varchar(128) NOT NULL,
  `expires_at` datetime NOT NULL,
  `consumed_at` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_confirmation_token` (`token_hash`),
  KEY `idx_confirmation_lookup` (`draft_id`, `revision`, `user_id`, `action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程草稿高风险操作确认';
```

高风险操作单独持久化，使超时、断连和重试都可判定：

```sql
CREATE TABLE `ai_flow_harness_operation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operation_id` varchar(64) NOT NULL,
  `draft_id` varchar(64) NOT NULL,
  `revision` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `action` varchar(32) NOT NULL COMMENT 'REAL_RUN|OVERWRITE_COMMIT|COMMIT',
  `status` varchar(32) NOT NULL COMMENT 'PENDING|RUNNING|SUCCEEDED|FAILED|UNKNOWN',
  `result_summary_json` text DEFAULT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_id` (`operation_id`),
  UNIQUE KEY `uk_operation_action` (`draft_id`, `revision`, `action`),
  KEY `idx_operation_owner` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI流程Harness高风险操作';
```

**设计要点：**
- `graph_json` 整体存 `FlowDefinition` 的 JSON，**不拆节点/边子表**——草稿是中间态，整体读写，拆表只增加复杂度。为
  `FlowDefinition` 提供专用 `read/write` codec；现有 `FlowJsonCodec` 只有 Map/List 编解码，不能直接作为完整草稿反序列化接口。
- `FlowDraft` 使用 Lombok 可变 Bean 以保持与现有领域模型风格一致，但可变对象不得跨 mutation 共享。codec 读取产生
  独立图对象；保存、编译、校验和事件投影之间通过深拷贝隔离，任何只读动作都不得改变 canonical `graph_json`。
- 所有 mutation 使用 `UPDATE ... WHERE draft_id=? AND user_id=? AND revision=? AND status='BUILDING'`；影响行数为 0
  时返回 `DRAFT_CONFLICT` 或 `DRAFT_NOT_FOUND`，不做最后写入者覆盖。`COMMITTED` 是终态，不能再改、跑或再次提交。
- `validate_draft` 不改变 `status`，只在 `WHERE draft_id=? AND revision=?` 仍命中时更新
  `last_validated_revision` 与报告摘要；每次 mutation 将这两个 revision 清空。`simulate_draft` 成功后以同一条件
  写入 `last_simulated_revision`，确保迟到的校验/模拟结果不会标记到新图版本。
- `draft_id` 使用不可预测随机值；工具从认证上下文取得 `user_id`。访问查询始终匹配 `draft_id + user_id`；若草稿
  `session_id` 非空，再强制当前 `session_id` 相等，当前会话为空也视为不匹配；草稿 `session_id` 为空时同一用户可
  跨会话访问。草稿 ID 不是授权凭据，Harness 工具不提供管理员跨用户绕过。
- `idx_update_time` 供过期清理：`DELETE WHERE update_time < NOW() - INTERVAL 7 DAY AND status IN ('BUILDING','ABANDONED')`
- `status` 让「已提交」的草稿可留档追溯，也可直接清理
- 默认预算：最多 100 节点、200 边、单模板 16 KiB、`nodeConfig` 32 KiB、一次工具回灌 24 KiB；通过
  `nebula.ai.harness.draft.*` 配置覆盖。摘要读取最多返回 200 条节点/边索引，详细读取按页返回。

### 5.2 过期清理

一个 `@Scheduled` 任务，默认每天清理 7 天前未提交的草稿。周期与开关走配置：

```yaml
nebula:
  ai:
    harness:
      draft:
        ttl-days: 7
        cleanup-cron: "0 30 3 * * ?"
        max-nodes: 100
        max-edges: 200
        max-template-bytes: 16384
        max-node-config-bytes: 32768
        max-tool-result-bytes: 24576
        read-page-size: 50
      commit:
        require-simulation: true
      operation:
        unknown-timeout-seconds: 30
```

---

## 六、执行流程

### 6.1 典型对话

```
用户：做一个流程，抓取指定URL的文章，总结成三句话，存到知识库

模型 ──▶ list_node_types(engineType="DAG") → 拿到权威类型清单
     ──▶ create_draft(name="文章摘要",
                      engineType="DAG")   → draftId=d_x7k2, revision=0
     ──▶ add_node(START, "start")        → ok
     ──▶ add_node(TOOL, "fetch",
                  toolCode="http_get")   → ok
     ──▶ add_node(PROMPT, "summarize")   → ✗ ERROR: 缺 promptTemplate
     ──▶ add_node(PROMPT, "summarize",
                  promptTemplate="总结：#{content}",
                  outputKey="summary")   → ok
     ──▶ connect(start→fetch)            → ok
     ──▶ connect(fetch→summarize)        → ok
     ──▶ validate_draft                  → ✗ ERROR: END 节点缺失
     ──▶ add_node(END, "end")            → ok
     ──▶ connect(summarize→end)          → ok
     ──▶ validate_draft                  → ✓ 通过
     ──▶ simulate_draft                  → ✗ UNRESOLVED_VAR:
                                             #{content} 不存在
                                             （fetch 的 outputKey 是 raw）
     ──▶ update_node("summarize",
                  patch={promptTemplate:"总结：#{raw}"}) → ok
     ──▶ simulate_draft                  → ✓ 全节点可达，数据流通
     ──▶ update_draft_metadata(
                  patch={flowCode:"article_summary"}) → ok
     ──▶ validate_draft                  → ✓ 新 revision 通过
     ──▶ simulate_draft                  → ✓ 新 revision 数据流通
     ──▶ commit_draft                    → flowCode=article_summary, v1
```

**对比现状**：这整个过程在现有实现里是**一次 `generate_flow` 调用**，`#{content}` 变量名写错这类问题**根本不会被发现**，直到用户手动运行才炸。

### 6.2 与 FlowCopilotService 的关系

`FlowCopilotService` 不再复用或维护自己的工具循环；原循环的模型调用、工具白名单、超时、异常隔离、审计和
反馈回灌全部收敛进 `FlowGenerationHarness`。这不是“换一组工具”，而是用 Harness 取代原来的生成控制面。
Service 自身只负责请求身份、HTTP/SSE 传输和事件投影。

1. `FlowGenerationHarness` 注册并调度 harness 工具集，强制模型遵循“读草稿 → 小步修改 → 校验/模拟 → 确认 → 提交”
2. 由 `draft.updated` 事件驱动前端实时预览画布；事件只带摘要和 revision，完整草稿按授权 `read_draft`
3. `real_run` / 覆盖提交的确认事件经同一 SSE sink 发送，但确认结果必须回到服务端确认接口，不能由 SSE 本身授权

**保留的既有能力**：few-shot 召回（场景②）、`search_tools`（场景④）在新工具集下继续有效——模型建图时仍能语义检索可用工具。

---

## 七、分批实施

| 批次 | 内容 | 产出 |
|---|---|---|
| **B0** | `FlowGenerationHarness`、`HarnessToolScheduler`、`HarnessRequest`/Prompt/Example SPI、事件契约与 `COPILOT_TOOL` / `FLOW_NODE` 工具范围 | Harness 接管 Copilot 的生成控制面，SDK 不依赖 manager |
| **B1** | 模块骨架 + 增量迁移 `ai_flow_draft` + `DatabaseDraftStore`/`DraftApplicationService` + 访问控制、revision CAS、上下文预算、摘要/分页读 + `list_node_types` 双引擎描述 + metadata/node/edge 独立 mutation（含 `disconnect`）+ **双引擎字段级校验** | 模型能安全地迭代建图、纠正错误边并延后确定 flowCode，两种引擎都能建 |
| **B2** | `validate_draft` 三层校验（`DagRuleSet` + `StateMachineRuleSet`）+ `DraftCommitter` 适配器 + 原子 `CREATE_ONLY` 提交 | 建图闭环可用，不会覆盖既有流程 |
| **B3** | `simulate_draft`：按类型模拟执行器、`SimulationLoopDriver`、`DagSimulator`、`StateMachineSimulator`（含环检测） | 数据流校验 + 带假设标记的终态分析 |
| **B4** | `DraftConfirmationStore` + `HarnessOperationStore` + `real_run_draft` 双阶段服务端确认、超时结果查询 + SSE 投影 + Copilot 切换 + **`generate_flow` 下线** + 过期清理 | 完整替换现有一把梭 |

每批可独立验证。B0 是前置条件，不能与 B1-B4 倒置；B1+B2 完成即比现状强（有迭代、精确校验和安全提交），B3 是质变。

**双引擎不拆批次的理由**：规则集是并列的 SPI 实现，同批做只是多写一个 `EngineRuleSet`；拆开反而要为「只支持 DAG」的中间态设计降级提示，得不偿失。

### 7.1 B1 落地状态（2026-08-07）

B1 已按本设计实现：增量脚本为 `script/V20260807__create_ai_flow_draft.sql`，全量初始化脚本同步包含
`ai_flow_draft`；`AiFlowDraftMapper.compareAndSet` 使用 `draft_id + user_id + revision + BUILDING` 单 SQL
条件更新，`DatabaseDraftStore` 只负责严格 JSON 快照与原子持久化，访问控制、session 附加匹配、预算、字段校验和
mutation 编排统一位于 `DraftApplicationService`。

已注册 `create_draft`、`update_draft_metadata`、`add_node`、`update_node`、`remove_node`、`connect`、
`disconnect`、`read_draft` 八个独立草稿工具，并以 Harness 版双引擎 `list_node_types` 替换 manager 旧同编码 Bean。
Manager 系统提示词已改为草稿优先；`generate_flow` / `derive_agent` 在 B4 正式下线前仅作为用户明确要求旧版立即落库时的兼容路径。
当前 B1 不提前实现 `validate_draft`、提交、模拟和真实运行，这些仍分别归 B2-B4。

B1 测试覆盖强用户边界、session 有条件匹配、空 flowCode、陈旧 revision 冲突、非法 mutation 不落库、状态机
扁平字段转换及歧义边零删除；模块与 manager 依赖链编译作为合入门槛。

### 7.2 `generate_flow` 下线步骤（B4）

下线是**替换**而非删除，须保证不留悬空引用：

1. 从 `FlowGenerationHarness` 的兼容工具白名单移除 `generate_flow`，换入 harness 工具集；旧
   `FlowCopilotService.COPILOT_TOOL_CODES` 随旧循环整体删除，不再保留平行白名单
2. 由 `HarnessEventSink` 投影层移除 `generate_flow → flow` 映射，改由 `commit_draft` 的 `flow.committed`
   事件触发既有前端 `flow` 事件
3. 删除 `GenerateFlowToolDefinition`（318 行）——其 `FlowSchema` 若仅被它使用则一并删
4. 检查 `ai_tool` 表：`ToolRegistrySynchronizer` 会同步工具镜像，需确认下线后旧行被清理或标记失效
5. `CopilotSystemPrompt` 重写：从「一次性生成」的指令改为「迭代建图」的工作流指令

> `derive_agent` **不下线**——它是从已有流程派生 Agent，与建图正交。

### 7.2 关键验收断言

| 批次 | 必须自动化验证的断言 |
|---|---|
| B0 | manager 的 DTO、提示词与 few-shot 仅通过 SDK SPI 注入；Copilot 的一次工具调用可在 `HarnessEventSink` 中看到开始、结束和审计事件；`FlowCopilotService` 不再直接调用模型或维护工具循环。 |
| B1 | A 用户不能读写 B 用户草稿；草稿 session 非空时其他/空 session 均不能访问，session 为空时同用户可跨会话访问；工具工作线程收到与请求线程相同的 `HarnessCallContext`；同一 revision 的两次 mutation 仅一次成功，另一次返回 `DRAFT_CONFLICT`；非法 mutation、编译校验和只读事件投影后 `graph_json` 与 revision 不变；错误边可经精确 `disconnect` 删除，歧义选择器不得批量删除；flowCode 为空可建图但不可提交。 |
| B2 | 两个并发 `CREATE_ONLY` 提交同一 flowCode 仅一个成功；已提交草稿不可再改/再提交；显式覆盖缺 expected version 或确认令牌必失败。 |
| B3 | 含 PROMPT、TOOL、AGENT_REACT、LOOP 的模拟不调用 `AiService`、真实 Tool 或子 Agent；所有假设 guard 在报告中标记，不能被报告为确定成功。 |
| B4 | 未确认、过期令牌、已消费令牌、用户不符或 revision 变化的 `real_run_draft` 全部拒绝；超时调用返回 `operationId` 且同 revision 不会重复启动；试跑不产生 `ai_flow` 三表写入，也不污染正式流程缓存。 |

---

## 八、待讨论细节

**已决策**（不再讨论）：
- ~~旧 `generate_flow` 是否保留~~ → **下线**，步骤见 7.1
- ~~STATE_MACHINE 支持时机~~ → **与 DAG 同批做**，规则集并列设计
- `FlowDraft` 沿用 Lombok 可变 Bean，但只在单次 mutation 内可变，Store/校验/编译/事件之间使用独立对象或深拷贝
- 状态机工具参数使用扁平字段，由转换器写入 `FlowNodeDefinition.stateType` 与 `nodeConfig.stateConfig`
- mutation API 按 `update_draft_metadata` / `add_node` / `update_node` / `remove_node` / `connect` / `disconnect`
  分别定义，不使用通用命令入口
- B1 实现 `list_node_types` 的 DAG/STATE_MACHINE 权威分组描述
- 草稿以 `userId` 为强边界；草稿 session 非空时附加会话匹配，session 为空时同用户可跨会话访问
- 创建草稿允许 `flowCode` 为空，提交前通过 `update_draft_metadata` 补齐；commit 不顺带修改元数据
- `update_node` / `update_draft_metadata` 使用 `patch + clearFields`；错误边通过精确 `disconnect` 自愈

**已规划到 B5**（见第十章）：范式库与质量召回

**待确认：**

1. **草稿与画布的实时联动**：`draft_updated` 事件推送频率（每个动作 vs 批量），前端是否实时重绘。
**已补充决策：**

1. `flowCode` 默认 `CREATE_ONLY`，冲突即拒绝；不自动加后缀、不静默覆盖。覆盖是显式高级操作，须 expected version 与服务端确认。
2. 草稿写入以 revision CAS 为准；`COMMITTED` 不可变。`diff_draft` 以后若增加，只做展示，不能替代 revision。
3. `AGENT_REACT` 模拟仅产出占位物，不模拟其内部工具选择；真实行为必须经最终真跑验收。
4. DAG 的未判定 `IF` 走有上限分支探索；状态机按 `sortNo` 作单路径假设。两者都将不确定性写入报告，超出覆盖上限只告警、不伪报通过。

---

## 九、B5：范式库与质量召回（后续批次）

> 本章是**方向规划**，不在 B1-B4 实施范围内。B4 会保留 few-shot 召回的接入点，B5 只需替换召回策略，不返工。

### 10.1 定位：harness 与 RAG 各管一半

一个常见的混淆需先厘清：

> **harness 规范的是「怎么把流程建出来」的过程，不是「流程本身长什么样」。**
> 建完的流程仍是同一张 DAG/状态机，运行时与手画的没有区别。harness 提高的是**建图正确率**，不是**流程运行质量**。

两者的分工：

```
harness 保证「建得对」  —— 结构合法、变量引用通、终态可达、能跑通
    +
RAG    供给「建得好」  —— 借鉴被验证过的优秀范式，少走弯路
    =
每次生成都站在历史最优解上
```

RAG 在 harness 里的角色是**外挂知识库**：模型遇到不确定的地方主动查询。项目里**已有半个实现**——`search_tools`（场景④）就是「模型不知道有哪些工具 → 去向量库查」。B5 把这个模式扩展到「流程范式」这类知识上。

### 10.2 现有缺口：召回只看相似，不看好坏

现有 few-shot 召回（场景②，`FlowExampleService.recall`）按**纯语义相似度**排序。这意味着：

- 一个建完即废弃的烂流程
- 一个跑了十万次的核心流程

在向量库里**权重完全一样**。相似度高的先出，与质量无关。

### 10.3 质量模型：平台权重为主，用户评分为参考

**核心决策：不做自动闭环。**

| | 角色 | 是否直接参与排序 |
|---|---|---|
| **平台权重** | 由平台方显式配置的确定性规则 | **是**——排序的唯一依据 |
| **用户评分** | 观察信号，供平台方参考 | **否**——只作为调权重的输入 |

**为什么不让评分自动影响排序：**

1. **确定性**：平台配的权重是可预期、可复现的。测试出来什么效果，线上就是什么效果。评分自动介入会让召回结果随时间漂移，难以复现问题。
2. **冷启动失控**：早期评分样本少，几条低分就能把一个好范式永久压死，且无人知晓原因。
3. **可追溯**：人工调权重每次变更有记录、可回滚；自动闭环的权重漂移无法归因。

**运作方式（人在环里）：**

```
用户评分 ──▶ 汇总展示（管理后台） ──▶ 平台方观察 ──▶ 手动调整权重 ──▶ 影响召回
                                        ▲                                    │
                                        └────────── 观察效果变化 ─────────────┘
```

### 10.4 数据模型（草案）

**范式库表** `ai_flow_pattern`：存**可复用的流程模式**，比具体业务流程更抽象。
例如「审批流：提交 → 审核 → (通过 | 打回重来)」这个**模式**，比某条具体的请假审批流更值得复用。

```sql
CREATE TABLE `ai_flow_pattern` (
  `id`           bigint      NOT NULL AUTO_INCREMENT,
  `pattern_code` varchar(64) NOT NULL COMMENT '范式编码',
  `name`         varchar(128)         COMMENT '范式名称',
  `description`  varchar(512)         COMMENT '语义描述（用于向量化召回）',
  `engine_type`  varchar(32)          COMMENT '适用引擎 DAG|STATE_MACHINE',
  `graph_json`   longtext             COMMENT '范式骨架（FlowDefinition）',
  `weight`       decimal(6,3) DEFAULT 1.000 COMMENT '【平台配置】排序权重，人工维护',
  `enabled`      tinyint      DEFAULT 1     COMMENT '是否参与召回',
  `source_flow_code` varchar(64)      COMMENT '来源流程（若从实际流程提炼）',
  `create_time`  datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_pattern_code` (`pattern_code`)
) COMMENT='AI流程范式库';
```

**评分表** `ai_flow_rating`：用户评分，**仅作观察信号**。

```sql
CREATE TABLE `ai_flow_rating` (
  `id`          bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(32) COMMENT 'PATTERN|FLOW',
  `target_code` varchar(64) COMMENT '被评对象编码',
  `user_id`     bigint,
  `score`       tinyint     COMMENT '评分 1-5',
  `comment`     varchar(512),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_target` (`target_type`,`target_code`)
) COMMENT='AI流程/范式用户评分（观察信号，不直接参与排序）';
```

> `weight` 在 `ai_flow_pattern` 上、`score` 在独立表里——这个分离本身就体现了「评分不直接参与排序」的设计意图。

### 10.5 召回排序

```
finalScore = semanticSimilarity × pattern.weight
```

- `semanticSimilarity`：向量库返回的余弦相似度
- `pattern.weight`：平台配置，默认 1.000；调高则该范式更容易被召回，调低则压制，置 0 或 `enabled=0` 则完全屏蔽

用户评分**不出现在这个公式里**，只在管理后台聚合展示（平均分、评分数、近期趋势），供平台方判断是否调 `weight`。

### 10.6 新增工具：`search_patterns`

与 `search_tools` 同构——模型建图前主动查询可借鉴的范式：

```json
{
  "ok": true,
  "patterns": [
    {
      "patternCode": "approval_loop",
      "name": "审批回退环",
      "description": "提交→审核→(通过进入终态 | 打回重新修改)，适合需要多轮修订的场景",
      "engineType": "STATE_MACHINE",
      "skeleton": { "nodes": ["submit","review","revise","publish"], "...": "..." }
    }
  ]
}
```

模型可直接以范式骨架为起点建图，再按具体需求裁剪——比从空图开始收敛快得多。

### 10.7 为什么放在 B5 而非更早

1. **依赖 harness 闭环先跑通**：没有「建得对」的保证，谈「建得好」无意义。
2. **需要积累期**：现在库里没有足够流程可供提炼范式、评价好坏，提前做排序是空转。
3. **零返工**：B4 保留 few-shot 接入点，B5 只替换召回策略（纯相似 → 相似×权重）与新增 `search_patterns` 工具。

---

## 十、附：关键既有资产复用清单

| 既有资产 | 位置 | 在 harness 中的用途 |
|---|---|---|
| `FlowDefinition` / `FlowNodeDefinition` / `FlowEdgeDefinition` | `nebula-sdk-ai/flow/` | 草稿的领域模型，直接复用 |
| `FlowGraphFactory` | `nebula-sdk-ai/flow/` | **validate 第二层**：编译即校验，免费拿成环/执行器缺失检测 |
| `ConditionCompiler` / `CondGroupCompiler` | `nebula-sdk-ai/flow/` | validate 第三层：SpEL 条件语法校验 |
| `FlowEngine` | `nebula-sdk-ai/flow/` | `runDefinition` 临时执行入口，供 `DraftRunner` 真跑草稿 |
| `FlowNodeExecutor` (SPI) | `nebula-sdk-ai/flow/` | 按类型注册模拟执行器；LOOP 使用独立模拟驱动器 |
| `ToolDefinition` / `ToolRegistry` | `nebula-sdk-ai/flow/` | 增加 `InvocationScope` 与严格参数校验；Harness 和流程节点双重授权 |
| `FlowJsonCodec` | `nebula-sdk-ai-flow/store/` | 节点 JSON 列编解码；草稿另补 `FlowDefinition` 专用 codec |
| `FlowAdminService` | `nebula-service-manager` | 由 manager 的 `DraftCommitter` 适配器调用，提供条件写入语义 |
| `FlowCopilotService` 既有能力 | `nebula-service-manager/ai/copilot/` | 迁移其模型调用、白名单、超时、审计和 SSE 事件语义到 `FlowGenerationHarness`；Service 最终仅保留传输适配 |
