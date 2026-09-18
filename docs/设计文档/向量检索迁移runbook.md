# 向量检索 collection 迁移 Runbook（批次4）

> 配套设计见《向量检索基建（Milvus）设计.md》第 6.4 节。本文是**人工运维操作手册**——迁移是低频、高危动作，
> 刻意不做成自动编排代码（脚本化比代码化更可控、可中途人工核验）。代码侧只把版本位参数化，见
> `MilvusCollections.SCHEMA_VERSION`。

## 何时需要迁移

- **换 embedding 模型或维度**（如 1536 → 1024）：物理名嵌维度，维度变即物理库变。
- **重建索引 / 调整 schema**（如换度量、改 HNSW 参数、加标量字段）：递增 `SCHEMA_VERSION`。

无上述变化时**不要迁移**。日常运行逻辑名（alias）稳定，上层无感。

## 核心模型

- **逻辑名**（`nebula_kb` / `nebula_flow_example` / `nebula_memory` / `nebula_tool_catalog`）：上层稳定引用，实为 Milvus **alias**。
- **物理名**：`{prefix}{logical}__d{dim}__v{SCHEMA_VERSION}`，如 `nebula_kb__d1536__v1`。由 `MilvusCollections.physicalName` 拼。
- 迁移 = 建新物理库 → 全量 backfill → **alias 原子切换** → 删旧物理库。切换瞬间完成，读写无感。

## 迁移步骤

以「知识库 KB 从 1536 维迁到 1024 维」为例（其余 collection 同理，逐个迁）。

### 0. 前置

- 确认新 embedding 模型/维度，更新配置 `nebula.ai.embedding.*`（**先别重启生产**）。
- 若是 schema 变更而非维度变更：先把 `MilvusCollections.SCHEMA_VERSION` 递增（如 1 → 2）并发布，使 `physicalName` 指向新版本位。
- 备份或确认真相源完整：KB 的真相源是 `ai_knowledge_chunk` 表，记忆是 `ai_memory` 表——backfill 从真相源重算，故真相源必须完整。

### 1. 建新物理库

新物理名：`nebula_kb__d1024__v1`。用与 `CollectionInitializer` 相同的 schema 建库（pk/vector/scene/content/create_ts + 该 collection 专有标量 + partitionKey + HNSW 索引）。可临时把 `auto-create-collection` 指向新维度启动一个实例建库，或用 Milvus 客户端手工建。

### 2. 全量 backfill

从真相源逐条重算向量写入**新物理库**（此时 alias 仍指旧库，线上读写不受影响）：

- **KB**：遍历 `ai_knowledge_chunk`，用新模型 embed `content` → upsert 到 `nebula_kb__d1024__v1`。
- **记忆**：遍历 `ai_memory`，embed `content` → upsert 到新库。
- **few-shot**：遍历 `ai_flow`，embed 名称+描述 → upsert。
- **工具目录**：从 `ToolRegistry.all()` 重新索引即可（无真相表，代码即源）。

backfill 期间失败的条目记录下来，切换前补齐；无法补齐的置 `need_reindex=1`（记忆）留对账兜底。

### 3. 校验

- 新库 entity 数 ≈ 真相源行数（允许空 content 等跳过的差额）。
- 抽样检索：同一 query 在新旧库结果 top-k 合理（模型换了不要求完全一致，但相关性不应崩）。

### 4. alias 原子切换

把逻辑名 alias 从旧物理库改指新物理库（Milvus `alterAlias`，原子操作）：

```
alterAlias(collectionName="nebula_kb__d1024__v1", aliasName="nebula_kb")
```

切换后上层用逻辑名 `nebula_kb` 的读写立即落到新库。**这一步是迁移的生效点**，前面都是准备。

### 5. 观察与删旧

- 切换后观察一段时间（召回质量、错误率、对账积压 `need_reindex=1` 行数）。
- 确认稳定后**人工**删除旧物理库 `nebula_kb__d1536__v1`。**删库不写进任何自动流程**——留人工确认，避免误删。

## 回滚

alias 未切换前：直接放弃新库即可，线上一直在旧库。
alias 已切换、发现问题：把 alias 改回旧物理库（旧库尚未删），即时回滚。**这是保留旧库到观察期结束的意义。**

## 记忆 clear 残留说明

`VectorLongTermMemory.clear`（清空某 agent/user 全部记忆）向量批量删失败时，因无单条 id 不置 `need_reindex`，残留向量成幽灵命中。此类残留不由增量对账覆盖，需在迁移或专项清理时全量比对处理（低频，影响面为「已清空用户的旧向量偶发命中」，可接受）。单条 `delete` 失败已即时置位由对账清理。
