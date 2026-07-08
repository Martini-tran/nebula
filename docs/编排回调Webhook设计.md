# nebula 编排回调（Webhook）设计 v1

> 本文档是 [跨实例迭代层设计.md](./跨实例迭代层设计.md) 与 [智能体设计.md](./智能体设计.md) 的增补，定义**编排引擎如何对外发通知**：节点/实例执行到某状态（成功/失败）时，按约定格式 POST 到可配置的回调 URL。
>
> 触发本设计的真实需求：**"迭代链每生成一篇文章，要把文章真正落进 blog 业务库"**。但本设计不把"调 blog"写死进引擎——而是抽象成通用回调：谁想接产物，自己起个 URL 配上即可。blog 落库只是第一个消费者。

---

## 一、为什么是 Webhook，而不是"引擎直接调 blog"

### 1.1 现状约束（关键事实）

排查确认：**nebula 各服务是独立进程，且项目当前零服务间调用**（无 Feign、无 RestTemplate/WebClient、无注册中心互调）。各服务只经 gateway 对外，服务之间从不互相依赖。

因此"让迭代驱动（manager）直接 HTTP 调 blog 的落库接口"意味着**引入项目第一套服务间调用基建**，且让引擎**硬编码认识 blog**——与现状割裂、耦合过重。

### 1.2 Webhook 把耦合方向反转

```
❌ 引擎认识 blog：  IterationDriver ──硬编码调用──▶ BlogPostService
✅ 引擎只认 URL：   引擎 ──POST 配置里的 URL──▶ ???（谁配谁消费，引擎不认识对方）
                                              └─ blog 起个接口接住 → 落库
                                              └─ 未来任何消费者同理
```

引擎只做一件事：**到达某状态时，把产物 POST 到配置的 URL**。blog 落库、通知第三方、触发下游流程……都是这个能力的消费者。**零服务间硬依赖，符合"服务只对外暴露、互不依赖"的现状。**

---

## 二、核心概念

| 概念 | 含义 |
|---|---|
| **回调点（Hook）** | 触发回调的位置 + 事件：`NODE_SUCCESS`/`NODE_FAILED`（节点级）、`INSTANCE_SUCCESS`/`INSTANCE_FAILED`（实例级） |
| **回调配置（WebhookConfig）** | `url` + `mode(SYNC/ASYNC)` + 可选 `headers`/`secret`。配在链上（迭代链）或流程/节点上，**链上优先** |
| **投递记录（Delivery）** | 每次回调落一行 `ai_webhook_delivery`：既是**日志**，又是**重发依据**。承载可靠性与断点续发 |
| **消费端（Consumer）** | 接住回调 POST 的接口（如 blog 的 series-append），按约定格式解析、幂等落库 |

---

## 三、回调配置：配在哪（决策：两处都支持，链上优先）

### 3.1 三个配置位，优先级自内向外

| 配置位 | 载体 | 适用 | 优先级 |
|---|---|---|---|
| **链级** | `ai_agent_iteration.webhook_url` / `webhook_mode` | 迭代链"每轮产物往哪送"——是链的属性（同一 Agent 被多链复用各送各处） | **最高** |
| **节点级** | `ai_flow_node.node_config.webhook` | 某节点成功/失败后通知（细粒度，如"审核通过"） | 中 |
| **流程级** | `ai_flow` 上配（或 agent 上） | 整个实例到终态后通知（粗粒度，非迭代链的普通流程也能用） | 最低 |

> **迭代链场景走链级**：博客系列"每轮产物送 blog"配在链上最直接，无需从节点取。链上没配才回落流程/节点配置。

### 3.2 WebhookConfig 结构

```jsonc
// 链级：ai_agent_iteration.webhook_url + webhook_mode 两列（简单场景够用）
// 节点/流程级：node_config.webhook / flow 扩展段（结构一致）
{
  "webhook": {
    "url": "http://blog-svc/admin/blog/webhook/series-append",
    "mode": "SYNC",                       // SYNC | ASYNC（决策见第四章）
    "events": ["INSTANCE_SUCCESS"],       // 关注哪些事件；缺省 [INSTANCE_SUCCESS, INSTANCE_FAILED]
    "headers": { "X-Token": "..." },       // 可选：消费端鉴权（本期先留字段）
    "contextKeys": ["articleTitle", "articleBody", "outline", "outlineDone"]  // 只送白名单键，缺省送全量
  }
}
```

---

## 四、回调对主流程永远非阻塞（关键澄清）

**必须分清两件事——原稿把它们混在一起，导致"SYNC 让这轮判失败"的错误论证：**

| 问题 | 答案 |
|---|---|
| **回调成败会阻塞/回滚主流程吗？** | **永远不会。** 实例已 SUCCESS 就是 SUCCESS（终态不可翻盘，对齐"Export 失败不翻盘终态"铁律）；链该 `advance` 就 `advance`。**这一点没有"同步阻塞"选项。** |
| **这次回调 HTTP，在哪个线程发？** | 这里才有"当场发 / 丢线程池发"之分，但它**只是发送时机的优化，不改变上面那条，也不改变可靠性**。 |

**所以：回调不存在"影响流程走向的同步模式"。可靠性只来自 `ai_webhook_delivery` 表 + 重发（第五章），与在哪个线程发无关。**

### 4.1 `mode` 降级为"发送时机"，纯性能取舍（W1 可先不做）

| mode | 含义 | 好处 | 代价 | 失败处理 |
|---|---|---|---|---|
| **INLINE**（原 SYNC） | 主流程收尾后，**同一线程当场 POST 一次** | 一次即成时立刻送达（博客立刻落库），省得等扫描器 | blog 慢则该线程多等几秒（但**不判失败**） | 与 DEFER 完全相同 ↓ |
| **DEFER**（原 ASYNC） | 落完 delivery 记录即返回，发送交独立线程池 | 主流程一点不等 | 多一跳 | 与 INLINE 完全相同 ↓ |

**两种 mode 的失败处理一模一样**：失败 → delivery 记 `FAILED` → 重发扫描器补发。这正是"可靠性不来自 mode、来自 delivery 表"的体现。

> **W1 简化**：先**不暴露 mode 配置**，统一用"INLINE 当场发一次 + 失败落表补发"。等真出现"blog 太慢拖累 Driver"再加 DEFER。

### 4.2 博客场景的最终语义（一句话）

> **advance 成功后，当场 POST 一次到 blog；发不成就落 `ai_webhook_delivery(FAILED)`，靠重发补——链不等它、不因它重跑 Agent、不因它翻盘实例。**

既拿到"当场发、立刻落库"的好处，又不会因一次网络抖动阻塞链或丢文章（delivery 表兜底）。

---

## 五、投递记录表：日志 + 断点续发合一（核心）

"记日志"与"某节点断了要重新执行这次"是同一件事的两面——都靠一张投递记录表实现。

### 5.1 DDL

> **表归属**：触发者（Driver / AgentEngine）都在 **manager 服务**，故本表落 **manager 库**（与 `ai_agent_iteration` 同库），blog 只是消费端不持有它。

```sql
CREATE TABLE ai_webhook_delivery (
  id             BIGINT       PRIMARY KEY AUTO_INCREMENT,
  delivery_id    VARCHAR(64)  NOT NULL COMMENT '投递唯一标识（业务键，重发幂等键之一）',
  instance_id    VARCHAR(64)           COMMENT '来源实例',
  chain_id       VARCHAR(64)           COMMENT '来源迭代链（若来自链）',
  seq            INT                   COMMENT '迭代链第几轮（幂等：chainId+seq 唯一定位一篇产物）',
  agent_code     VARCHAR(64),
  node_code      VARCHAR(64)           COMMENT '节点级回调才有',
  event          VARCHAR(32)  NOT NULL COMMENT 'NODE_SUCCESS|NODE_FAILED|INSTANCE_SUCCESS|INSTANCE_FAILED',
  url            VARCHAR(512) NOT NULL COMMENT '发去哪',
  mode           VARCHAR(8)   NOT NULL DEFAULT 'INLINE' COMMENT 'INLINE(当场发) | DEFER(线程池发)；仅发送时机，与可靠性无关',
  payload        TEXT         NOT NULL COMMENT '发了什么（JSON，重发时直接读它，不重跑 Agent）。注意：不含鉴权 headers（见下）',
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | SUCCESS | FAILED',
  attempts       INT          NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  max_attempts   INT          NOT NULL DEFAULT 8 COMMENT '重发上限，达到后置 DEAD 不再自动重发（留手动/告警）',
  response_code  INT                   COMMENT '最后一次 HTTP 响应码',
  last_error     TEXT                  COMMENT '最后一次失败原因',
  next_retry_at  DATETIME              COMMENT '下次重发时间（FAILED 且未达上限时，按指数退避）',
  create_time    DATETIME,
  update_time    DATETIME,
  UNIQUE KEY uk_delivery_id (delivery_id),
  KEY idx_chain_seq (chain_id, seq),
  KEY idx_retry (status, next_retry_at)   -- 重发扫描走此索引
) COMMENT='编排回调投递记录（日志 + 断点续发）';
```

> **status 全集**：`PENDING`（待发）→ `SUCCESS`（2xx）/ `FAILED`（可重发）→ 达 `max_attempts` 转 `DEAD`（停发，告警/手动）。
> **鉴权 headers 不落库明文**：`payload` 只存回调 body；`WebhookConfig.headers`（可能含 `X-Token`/签名密钥）**不写进 delivery 表**——重发时从配置源（链/流程/节点 config）**实时取**，避免明文/过期凭据留在日志表里。

### 5.2 三诉求如何各自落地

| 你的诉求 | 由这张表如何实现 |
|---|---|
| **记日志** | 表本身就是日志：发了什么（payload）、去哪（url）、成没成（status/response_code）、失败原因（last_error）、几次（attempts） |
| **断点续发**（某节点断了重新执行这次） | 决策 = **只重发回调**：扫 `status != SUCCESS` 的记录，读回 `payload` **重发**——不重跑 Agent、不重调 LLM（产物已在 payload 里）。与现有恢复铁律"重放=恢复产物不重新执行"一致 |
| **幂等** | 重发带同一 `delivery_id`（或 `chainId+seq`）；消费端据此去重，重发不重复落库（对齐迭代链 at-least-once） |

### 5.3 "只重发回调" vs "重跑整轮"（决策：只重发回调）

- **只重发回调**（本设计）：实例已 SUCCESS、产物已在 `payload`，仅回调没送达 → 从 delivery 表读 payload 重发。**便宜、无副作用、不重复计费**。
- 重跑整轮（不采用）：连 Agent 重新生成，重调 LLM，产出可能不同，耗 token。仅"产物本身错了"才需要，不在本设计。

### 5.4 重发触发

- **当场失败**：INLINE 当场发失败 → 落 `FAILED` + `next_retry_at`（指数退避）。**不判实例/链失败**——实例终态不可翻盘，链照常 advance；这条回调交重发扫描器补（见下）。
- **定时补偿**：一个定时扫描器（复用 `IterationScheduler` 同款 `@Scheduled`）扫 `status=FAILED AND next_retry_at<=now AND attempts<max_attempts`，重发；达上限转 `DEAD`，停发留告警/手动。
- **手动重发**：REST `POST /admin/.../webhook/{deliveryId}/retry` 手动补发某条（含 `DEAD` 的）。

> **W1 空窗说明（重要）**：重发扫描器属 **W2**。故 **W1 阶段若 INLINE 当场发失败，仅落 `FAILED` 记录、无自动补发**——需靠手动重发或下次同链人工介入。W1 的博客 demo 网络抖动可能丢一篇（有 delivery 记录可查），这是 W1 的已知空窗，W2 补齐后消除。若不接受此空窗，W1 就要连带把"手动重发 REST"一起做（最小补救）。

---

## 六、触发点：三个位置，各自触发时机不同（原稿把它们混成一个，是最大缺陷）

回调触发在**引擎/驱动的终态钩子**，不塞进业务节点。但**迭代链每轮回调**和**实例/节点级回调**是**两个不同的触发点**，混用会发出"假成功"回调：

| 触发点 | 在哪触发 | 为什么在这 |
|---|---|---|
| **① 迭代链每轮**（链级 webhook） | **`IterationDriver.advanceOne` 中 `advance` CAS 成功之后** | advance 可能 CAS 失败（多实例被别的节点抢先，见 [IterationDriver.java:146](../nebula-sdk/nebula-sdk-ai/src/main/java/com/nebula/common/ai/iteration/IterationDriver.java#L146)）。若在 `AgentEngine.run` 内部就发，**advance 失败后仍发出回调 → blog 落库、但链没推进 → 下轮重跑同一篇 → 重复落库**（虽被 `(chainId,seq)` 幂等兜住，但触发点逻辑错位）。**必须 advance 成功才发。** |
| **② 实例级**（流程级 webhook） | `AgentEngine.run` 在 `exportIfTerminalSuccess(...)` 之后 | 非迭代链的普通实例，到终态即通知。此时实例已落库、产物在 `ctx`，无 advance 顾虑。 |
| **③ 节点级**（`node_config.webhook`） | 内核节点 SUCCESS 记账后，经 `TransitionListener`（[StateMachineOrchestrator.java:211](../nebula-sdk/nebula-sdk-ai/src/main/java/com/nebula/common/ai/orchestration/statemachine/StateMachineOrchestrator.java#L211) `onStateSucceeded` / L221 `onTerminal`）回调 | 细粒度（如"审核节点通过后通知"）。 |

> **配置优先级仍是链级 > 节点级 > 流程级**（第三章）；但**链级 webhook 的发送时机是 Driver advance 之后**，不与②③同点。迭代链场景走①。

```
触发（① advance 成功后 / ② 实例 export 后 / ③ 节点记账后）
   → 解析 webhook 配置（链级 > 节点级 > 流程级）
   → 命中 events 才继续
   → 组装 payload：从 ctx 取键，先剔除内部控制键（__smSuspended / CURRENT_INSTANCE_KEY /
                   MAX_AGENT_DEPTH_KEY / SIGNAL_EVENT_KEY / CALL_STACK_KEY，对齐 manager 的 INTERNAL_KEYS），
                   再按 contextKeys 白名单收窄（缺省=剔除内部键后的全量）
   → 落 ai_webhook_delivery(PENDING)
   → INLINE：当前线程发，结果回写 delivery（失败落 FAILED，不阻塞主流程）
     DEFER：交执行器发，主流程继续，结果异步回写 delivery
```

> **与 SDK 解耦**：SDK 侧只定义 `WebhookDispatcher` SPI（"给我一个 delivery，我负责发+记录"）；迭代链回调直接在 `IterationDriver` 里调 dispatcher（Driver 本就是编排层普通代码）。HTTP 实现与 delivery 落库放能拿到基建的模块（与 `IterationLock`/`AgentInstanceStore` 同一分层）。SDK 不引入 HTTP 客户端。

---

## 七、回调 body 约定（消费端按此解析）

POST JSON：

```jsonc
{
  "deliveryId": "wh-xxxx",
  "event": "INSTANCE_SUCCESS",
  "instanceId": "blog_series_writer-xxxx",
  "chainId": "blog_series_writer-chain-demo01",
  "seq": 3,                          // 迭代链第几轮（= 第几篇）
  "agentCode": "blog_series_writer",
  "nodeCode": null,                  // 节点级才有
  "state": "node_3",
  "context": {                       // 先剔除内部控制键，再按 contextKeys 白名单收窄（缺省=剔除后全量）
    "articleTitle": "第3篇：泛型",
    "articleBody": "...",
    "outline": "...",
    "outlineDone": "false"
  },
  "timestamp": "2026-07-08T09:00:03"
}
```

> **`context` 缺省不是"裸全量"**：先剔除内部控制键（`__smSuspended`/`CURRENT_INSTANCE_KEY`/`MAX_AGENT_DEPTH_KEY`/`SIGNAL_EVENT_KEY`/`CALL_STACK_KEY`），再按 `contextKeys` 白名单收窄。避免把引擎内部状态泄进回调 body。

消费端（blog）约定：以 `(chainId, seq)` 或 `deliveryId` 幂等去重；首轮（seq=0 或按 chainId 查不到系列）建 `blog_series` 并记 `chain_id` 映射，后续轮建 `blog_post`(status=**draft**, sourceType=AI) 并挂到系列目录。

---

## 八、落地路线

**阶段 W1 · 投递记录 + 迭代链回调（INLINE 当场发）+ 手动重发兜底**
- `ai_webhook_delivery` 表（manager 库）+ store
- 链级配置：`ai_agent_iteration` 加 `webhook_url`（W1 只需 url；mode 缺省 INLINE，暂不暴露）
- `WebhookDispatcher` SPI + HTTP 实现（组 payload → 落 delivery → 发 → 回写）
- **①迭代链触发点**：`IterationDriver` 在 `advance` **成功之后**调 dispatcher（不在 `AgentEngine.run` 内）
- **手动重发 REST**（`POST /admin/.../webhook/{deliveryId}/retry`）：W1 就带上，补上"无自动重发扫描器"的空窗
- **Demo：迭代链 → blog 落库**：blog 提供 `POST /admin/blog/webhook/series-append`（幂等建系列+文章+挂目录，草稿状态）

**阶段 W2 · 自动重发 + 实例/节点级触发 + DEFER**
- 定时重发扫描器（扫 `FAILED AND next_retry_at<=now AND attempts<max_attempts`，指数退避，达上限转 `DEAD`）
- ②实例级（`AgentEngine.run` export 后）+ ③节点级（`node_config.webhook`）触发点
- DEFER 发送时机（线程池，复用 blog `@EnableAsync` 同款执行器）

**阶段 W3 · 治理**
- 消费端鉴权（headers/secret 签名，headers 不落库、重发时实时取）、`DEAD` 告警、投递记录管理页

---

## 九、决策记录

- ✅ **Webhook 而非引擎直调 blog**：项目零服务间调用，引擎只认 URL 不认 blog，耦合方向反转，符合"服务互不依赖"现状。
- ✅ **配置两处都支持、链上优先**：迭代链走链级（`webhook_url`，W1 只需 url），普通流程走流程/节点级。
- ✅ **回调对主流程永远非阻塞**（修正原稿）：无"同步阻塞判失败"选项——实例终态不可翻盘、链照常 advance；可靠性只来自 `ai_webhook_delivery` + 重发。
- ✅ **`mode` 降级为发送时机**（`INLINE` 当场发 / `DEFER` 线程池发），纯性能取舍、失败处理相同；W1 只做 INLINE，不暴露配置。
- ✅ **三触发点分离**（修正原稿最大缺陷）：①迭代链在 `IterationDriver` **advance 成功后**（防 advance 失败发假成功）；②实例级在 `AgentEngine.run` export 后；③节点级在内核记账后。
- ✅ **投递记录表 = 日志 + 断点续发合一**：`ai_webhook_delivery` 存 payload，重发读它；`PENDING→SUCCESS/FAILED→DEAD`，`max_attempts` 封顶。
- ✅ **只重发回调不重跑整轮**：产物已在 payload，重发不重调 LLM，与恢复铁律"重放=恢复产物不重新执行"一致；链即使回调失败也照常 advance。
- ✅ **幂等靠 `(chainId, seq)` / `deliveryId`**：消费端去重，对齐迭代链 at-least-once。
- ✅ **payload 剔除内部控制键**（缺省不裸送全量）；**鉴权 headers 不落库**，重发时从配置源实时取。
- ✅ **表落 manager 库**：触发者（Driver/AgentEngine）在 manager，与 `ai_agent_iteration` 同库；blog 仅消费不持有。
- ✅ **W1 空窗已知**：自动重发在 W2，W1 靠手动重发 REST 兜底。
- ✅ **与 SDK 解耦**：`WebhookDispatcher` SPI，HTTP 实现外置，SDK 不引入 HTTP 客户端（同 `IterationLock` 分层）。
- ✅ **AI 生成文章落 draft 草稿**：人工审阅后再发布。
