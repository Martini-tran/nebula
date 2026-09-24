# nebula 写作 Agent（Scribe）设计 v1

> 本文档回答一个具体问题：
>
> **「小说写作台的 AI 辅助，落在哪一层？人物设定与故事编年存在哪里？」**
>
> **核心结论先说：**
>
> 1. **scribe 是一个全新业务服务，不是 manager 的一个模块。** 它自己持有作品/章节/设定/编年五张表，
>    通过 `AgentEngine` 消费 AI 能力——**和 blog 消费迭代链是同一个姿势**（见 [编排回调Webhook设计.md](编排回调Webhook设计.md) 第七章）。
> 2. **人物设定绝不能存 `ai_memory`。** `ai_memory` 的隔离键是 `(agent_code, user_id)`，**没有「哪本书」这一维**，
>    且 `AgentEngine` 的 Import 是「拿 key 名当检索词取 top-1」、Export 硬编码 `SEMANTIC` 且
>    **声明的 REPLACE/SUMMARY 策略并未实现**（源码注释自认）。人物卡需要的是「精确改第 3 条属性」，
>    不是「再 append 一行」。**这是本设计最重要的一个否决。**
> 3. **设定库分两层存**：结构化事实进 scribe 自己的 `scribe_codex_attr`（可锁定、可 diff、可精确改写）；
>    长文本进 `ai_knowledge_base`（`kb_code = work-{workId}`），复用现成的 chunk→embed→search 管线，**零新基建**。
> 4. **AI 永不静默写库。** 所有抽取结果都是**提议**，落 `scribe_proposal` 表，用户确认才 apply。
>    这套「提议—确认—落库」在 `nebula-sdk-ai-harness` 的 draft/CAS/commit 里已有完整先例，**照搬模式，不复用代码**（它绑死 `FlowDraft`）。
>
> **本批次交付**：设计与落地批次划分。**不含实现代码**。前端原型见 `ui-html/scribe-editor.html` 与 `ui-html/scribe-world.html`。
>
> **本文档不重复**：`AgentEngine` 门面语义、事件模型、工具治理——见 [AgentHarness设计.md](AgentHarness设计.md)。

---

## 〇、先厘清

| 维度 | **是** | **不是** |
|---|---|---|
| scribe 的定位 | 独立业务服务，自持数据，调 `AgentEngine` | manager 里的一个 admin 模块 |
| 人物设定的家 | `scribe_codex` + `scribe_codex_attr`（业务表） | `ai_memory`（隔离维度不够，且无精确改写） |
| 长文本检索 | `ai_knowledge_base`，`kb_code = work-{id}` | 自建一套向量管线 |
| AI 的权限 | 只能**提议**，写 `scribe_proposal` | 直接 UPDATE 设定表 |
| 「记录对话」 | 三件不同的事，三张表（见第三章） | 一张 chat_message 表通吃 |
| 时间 | **双轴**：叙述序（章节）+ 故事内时间 | 一条时间线 |

---

## 一、背景与现状（已核验）

### 1.1 前端已有，后端全空

`ui/nebula-scribe/` 是一个 Vue 3 应用，端口 28259，`/api` 代理到网关 19000。
**它目前 100% 跑在 mock 上**（`VITE_USE_MOCK=true`）。

后端侧的事实，逐条核验过：

| 检查项 | 结果 |
|---|---|
| `nebula-services/` 下有无 scribe | **无**。只有 blog / forge / gateway / manager / space |
| 全仓 grep "scribe"（java/sql/xml/yml） | **零命中**，只有 `describe`/`Subscriber` 的子串误报 |
| `script/nebula.sql` 有无 work/chapter/codex 表 | **无** |
| `gateway-routes.yml` 有无 `/scribe/**` | **无**。只有 manager(8081)/blog(8082)/space(8083)/forge(8884) |

> **所以这是一次 greenfield。** 前端的 TypeScript 类型是目前**唯一的契约**，本设计以它为准，不另起炉灶。

### 1.2 前端已定义的契约（直接采信）

`ui/nebula-scribe/src/types/` 下已定义完整：

- `work.ts` — `WorkListItem` / `WorkDetail` / `Volume` / `ChapterListItem` / `ChapterDetail`；
  `WorkStatus = draft|serializing|paused|finished`；`ChapterStatus = outline|drafting|revising|done`。
- `codex.ts` — `CodexKind = character|location|faction|item|lore`；
  `CodexEntry { id, workId, kind, name, aliases[], summary, detail, avatarUrl, tags[], pinned }`。
- `ai.ts` — 8 种 `AiAction`、5 种 `STYLE_PRESETS`、`AiGenerateRequest` / `AiGenerateResult`。

API 路径也已写死：`/scribe/works/**`、`/scribe/works/{workId}/codex`、`POST /scribe/ai/generate`。

> **本设计的 DDL 与 REST 必须向这套类型对齐**，前端只需把 `USE_MOCK` 关掉即可接通。

### 1.3 可直接复用的基建

| 能力 | 位置 | 复用方式 |
|---|---|---|
| chunk→embed→store→search | `nebula-sdk-ai-rag` `KnowledgeService` | 建 `kb_code = work-{id}` 的知识库，零改造 |
| `ToolDefinition` + `ToolRegistry` | `nebula-sdk-ai` | 新增 `codex_search` 等工具＝一个 Spring bean |
| `AgentEngine` 门面 | `nebula-sdk-ai` | scribe 的 Service 层调它，**不碰 `AiService`** |
| webhook 消费产物 | `SeriesIterationWebhookController`（blog） | 长任务产物回调 scribe 落库，同姿势 |
| 提议—确认—落库 | `nebula-sdk-ai-harness` draft/commit | **照搬模式**，代码绑死 `FlowDraft` 不可直接用 |

---

## 二、硬约束（决定方案走向，均已核验源码）

**C1. `ai_memory` 没有「作品」维度。**
`script/nebula.sql:459` 起，列为 `agent_code` / `user_id` / `conversation_id` / `mem_type` / `content` / `metadata`。
隔离索引是 `idx_agent_user(agent_code, user_id)`，且 `agent_code` 是 **Milvus 的 partitionKey**。
一个用户写三本书，三本书的人物会**混在同一个 `(agent_code, user_id)` 分区里**。
→ 想隔离只能给每本书造一个合成 `agent_code`，这会把 partition 基数从「Agent 个数」抬到「用户数 × 作品数」，
且与「`agent_code` = Agent 身份，各版本共享记忆」的既定语义直接冲突。

**C2. Memory Import 是 top-1 关键词召回，精度不够。**
`AgentEngine.java:547-570`：对每个 `importKey`，`setText(key).setTopK(1)`，
**拿 key 的名字当检索意图，取第一条的 content 写进 context**。
→ 「recall 关于沈砚的一切」这种需求，它给不了。

**C3. Memory Export 硬编码 `SEMANTIC`，且策略未实现。**
`AgentEngine.java:589-617`：`setType(MemoryType.SEMANTIC)` 写死；
方法上的注释明写「策略 Replace/Append/Summary 中 Summary 的 LLM 摘要留待有真实需求时接入，**当前按 Append 语义写入**」。
`MemoryType.ENTITY`（「人、物、概念及其属性」）**定义了但引擎从不写入**。
→ 人物卡要的是「把瞳色从浅褐改成深褐」，Export 只会再插一行，两行并存，召回时打架。

**C4. 全局能力开关默认全关。**
`nebula.ai.rag.knowledge.enabled` / `.fewShot.enabled` / `.toolSearch.enabled` / `.reconcile.enabled` 默认 `false`；
`nebula.ai.rag.memory.mode` 默认 `db`（LIKE 关键词检索，不走向量）。
→ scribe 要用向量检索，须显式开 `knowledge.enabled=true` 并部署 Milvus。**不能假设它已经开着。**

**C5. Milvus 字段有长度上限。**
`CollectionInitializer`：`content` VarChar(8192)，标量 VarChar(256)。
→ 章节正文必须切块入库，人物 `detail` 超长也要切。

**C6. `knowledge_search` 当前不可被 Copilot 调用。**
它没有覆写 `invocationScopes()`，默认只有 `FLOW_NODE`。
→ scribe 若要做对话式创作助手（COPILOT 形态），需要新增或放开 scope。

**C7. `PageResult` / `R` 已是全仓统一返回。**
`nebula-sdk-core` 的 `R<T>{code,message,data}` 与 `PageResult<T>{records,total,current,size,pages}`。
前端 `work.ts` 的注释已明确对齐 `PageResult`。

---

## 三、「记录故事发展、对话」是三件事

需求里的「记录」其实混了三种东西，它们的**生命周期、结构、消费方式完全不同**，
用一张表通吃会同时做不好三件事。拆开：

| # | 记的是 | 特征 | 落表 | 谁消费 |
|---|---|---|---|---|
| **①** | **故事内的事实**<br>（某章某人做了什么、关系变化、伏笔埋收） | 随章节单调增长；有双时间轴；需按人物/章节交叉查 | `scribe_chronicle` | 一致性检查、续写上下文 |
| **②** | **人物对话台词**<br>（角色说过的原话） | 是**语料**不是记录；用来维持每个角色的说话方式 | `scribe_voice_sample` | 写对白时做 few-shot |
| **③** | **我与 AI 的对话**<br>（创作过程、采纳了哪条建议） | 是**决策留痕**；需要「这条建议最终落在哪一段」的链接 | `scribe_dialog` + `scribe_dialog_message` | 回溯创作决策 |

> **为什么②要单独存而不是塞进人物 detail**：
> 「性格：冷淡」这四个字对模型几乎没有约束力，写出来的对白千人一面。
> 而 12 条真实台词能让模型抓到「短句、不用感叹号、习惯用数字回应」这种**可模仿的语感**。
> 这是 few-shot 而非 instruction，**必须是结构化的样本集合**，不能是一段散文里的几句引文。

---

## 四、数据模型

### 4.1 分层原则

```text
┌─ 结构化事实（要精确改写 / 要锁定 / 要 diff）────────────┐
│  scribe_codex_attr    人物硬事实：瞳色=浅褐、年龄=三十二   │  ← MySQL 权威
│  scribe_chronicle     编年：第4章/第4夜 沈砚发现空屋       │
│  scribe_relation      关系：沈砚 --怀疑--> 柳三娘          │
└────────────────────────────────────────────────────┘
              │ 变更时同步投影
              ▼
┌─ 长文本检索（要语义召回 / 不要求精确）──────────────────┐
│  ai_knowledge_base   kb_code = work-{workId}            │  ← 复用 RAG
│    doc_id = codex-{id} / chapter-{id} / chronicle-{id}  │
└────────────────────────────────────────────────────┘
```

**读写规则**：
- **写**：一律先写 MySQL（真相源），成功后投影进 KB。投影失败只 warn + 标记待重索引，**不回滚业务**
  （与 `VectorLongTermMemory` 的 `need_reindex` 同构）。
- **读**：要精确值（瞳色、年龄）→ 直接查 `scribe_codex_attr`；
  要语义相关（「和这段情节有关的设定」）→ 走 `knowledge_search`。

> **不用 `ai_memory` 的理由收敛成一句**：它是「模糊召回的旁路」，而人物设定是「必须逐字正确的主干」。
> C1/C2/C3 三条硬约束任意一条都足以否决它。

### 4.2 DDL

```sql
-- ── 作品 / 卷 / 章节 ──────────────────────────────────────────────
-- 定稿见 script/mysql/nebula.sql（2026-09-23 修订：对齐起点/番茄建书字段，平台差异拆到 scribe_work_platform）
CREATE TABLE `scribe_work` (
  `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '作品ID',
  `user_id`           bigint       NOT NULL COMMENT '归属用户（所有访问均须匹配）',
  `title`             varchar(100) NOT NULL COMMENT '作品标题（作者自用，平台书名见 scribe_work_platform）',
  `summary`           varchar(200) DEFAULT NULL COMMENT '一句话简介（列表卡片）',
  `logline`           varchar(300) DEFAULT NULL COMMENT '核心立意/冲突（AI上下文用，不对外）',
  `intro`             text         COMMENT '作品简介，纯文本（平台档案默认简介）',
  `audience`          varchar(16)  DEFAULT NULL COMMENT 'male|female|general',
  `genre`             varchar(32)  DEFAULT NULL COMMENT '题材（作者自定义）',
  `tags`              json         DEFAULT NULL COMMENT '标签数组',
  `protagonists`      json         DEFAULT NULL COMMENT '主角名数组',
  `cover_file_id`     bigint       DEFAULT NULL COMMENT '封面（关联sys_file）',
  `status`            varchar(20)  NOT NULL DEFAULT 'draft' COMMENT 'draft|serializing|paused|finished',
  `target_word_count` int          DEFAULT NULL COMMENT '目标总字数，用于进度条',
  `word_count`        int          NOT NULL DEFAULT 0 COMMENT '累计字数（章节保存时增量维护）',
  `chapter_count`     int          NOT NULL DEFAULT 0,
  -- kb_code（本书知识库编码 work-{id}）推迟到 AI 批次再加
  `create_by`/`create_time`/`update_by`/`update_time`/`deleted`/`delete_time`  -- 全仓审计+软删除约定
  PRIMARY KEY (`id`),
  KEY `idx_scribe_work_user_status` (`user_id`, `deleted`, `status`),
  KEY `idx_scribe_work_user_update` (`user_id`, `deleted`, `update_time`)
) COMMENT='写作台作品表';

-- 【保留表，暂不建】作品在各第三方平台（起点/番茄/晋江…）的发布档案，一作品 N 平台。
-- 平台书名/简介/分类/标签体系各不相同，不塞进 scribe_work。到「导出/发布」批次再落地。
CREATE TABLE `scribe_work_platform` (
  `id`                      bigint       NOT NULL AUTO_INCREMENT,
  `user_id`                 bigint       NOT NULL COMMENT '冗余，便于按用户鉴权',
  `work_id`                 bigint       NOT NULL,
  `platform`                varchar(32)  NOT NULL COMMENT 'qidian|fanqie|jjwxc|zongheng|other',
  `platform_name`           varchar(50)  DEFAULT NULL COMMENT 'platform=other 时填写',
  `platform_book_id`        varchar(64)  DEFAULT NULL COMMENT '平台书号',
  `platform_url`            varchar(500) DEFAULT NULL,
  `title`                   varchar(100) DEFAULT NULL COMMENT '平台书名，空则沿用作品标题',
  `intro`                   varchar(1000) DEFAULT NULL COMMENT '平台简介，纯文本（起点 20~500 字）',
  `audience`                varchar(16)  DEFAULT NULL COMMENT '平台频道 male|female',
  `category`                varchar(32)  DEFAULT NULL COMMENT '平台一级分类，如 玄幻',
  `sub_category`            varchar(32)  DEFAULT NULL COMMENT '平台二级分类，如 东方玄幻',
  `tags`                    json         DEFAULT NULL COMMENT '取自该平台标签库',
  `protagonists`            json         DEFAULT NULL,
  `cover_file_id`           bigint       DEFAULT NULL COMMENT '空则沿用作品封面',
  `sign_status`             varchar(16)  NOT NULL DEFAULT 'unsigned' COMMENT 'unsigned|applying|signed',
  `publish_status`          varchar(16)  NOT NULL DEFAULT 'none' COMMENT 'none|serializing|finished|removed',
  `published_chapter_count` int          NOT NULL DEFAULT 0,
  `extra`                   json         DEFAULT NULL COMMENT '平台特有字段兜底',
  `remark`                  varchar(500) DEFAULT NULL,
  -- 审计+软删除同上；(work_id, platform) 唯一性在服务层校验（软删除与唯一索引冲突）
  PRIMARY KEY (`id`),
  KEY `idx_scribe_work_platform_work` (`work_id`, `platform`, `deleted`),
  KEY `idx_scribe_work_platform_user` (`user_id`, `deleted`)
) COMMENT='作品平台发布档案表（保留）';

CREATE TABLE `scribe_volume` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `work_id`    bigint       NOT NULL,
  `title`      varchar(128) NOT NULL,
  `sort_order` int          NOT NULL DEFAULT 0,
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_sort` (`work_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-卷';

CREATE TABLE `scribe_chapter` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `work_id`     bigint       NOT NULL COMMENT '冗余，避免查章节还要 join 卷',
  `volume_id`   bigint       NOT NULL,
  `title`       varchar(128) NOT NULL,
  `sort_order`  int          NOT NULL DEFAULT 0,
  `status`      varchar(16)  NOT NULL DEFAULT 'outline' COMMENT 'outline|drafting|revising|done',
  `synopsis`    varchar(1024) DEFAULT NULL COMMENT '本章梗概',
  `content`     longtext     COMMENT '正文（真相源）',
  `word_count`  int          NOT NULL DEFAULT 0,
  `indexed_at`  datetime     DEFAULT NULL COMMENT '最近一次投影进 KB 的时间，null=待索引',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_sort` (`work_id`, `sort_order`),
  KEY `idx_volume_sort` (`volume_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-章节';

-- ── 设定库 ────────────────────────────────────────────────────────
CREATE TABLE `scribe_codex` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `work_id`     bigint       NOT NULL,
  `kind`        varchar(16)  NOT NULL COMMENT 'character|location|faction|item|lore',
  `name`        varchar(128) NOT NULL,
  `aliases`     json         DEFAULT NULL COMMENT '别名数组，正文高亮与检索用',
  `summary`     varchar(512) DEFAULT NULL COMMENT '一句话概述',
  `detail`      mediumtext   COMMENT '详细设定（Markdown），超长切块入 KB',
  `avatar_url`  varchar(512) DEFAULT NULL,
  `tags`        json         DEFAULT NULL,
  `pinned`      tinyint(1)   NOT NULL DEFAULT 0 COMMENT '1=每次生成默认注入上下文',
  `first_seen_chapter_id` bigint DEFAULT NULL COMMENT '首次出场章节',
  `indexed_at`  datetime     DEFAULT NULL,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_kind` (`work_id`, `kind`),
  KEY `idx_work_pinned` (`work_id`, `pinned`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-设定条目';

-- 硬事实：拆成行而非塞进 detail，因为要精确改写 + 锁定 + diff
CREATE TABLE `scribe_codex_attr` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `codex_id`   bigint       NOT NULL,
  `work_id`    bigint       NOT NULL COMMENT '冗余，便于按书批量校验',
  `attr_key`   varchar(64)  NOT NULL COMMENT '字段名：瞳色/年龄/核心欲望…',
  `attr_value` varchar(1024) DEFAULT NULL,
  `locked`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '1=AI 不得改写，一致性检查以此为准绳',
  `sort_no`    int          NOT NULL DEFAULT 0,
  `source_chapter_id` bigint DEFAULT NULL COMMENT '出处章节，null=作者手填',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_codex_key` (`codex_id`, `attr_key`),
  KEY `idx_work_locked` (`work_id`, `locked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-设定硬事实';

-- 关系：有向 + 可随章节演变，故带生效区间
CREATE TABLE `scribe_relation` (
  `id`           bigint       NOT NULL AUTO_INCREMENT,
  `work_id`      bigint       NOT NULL,
  `from_codex_id` bigint      NOT NULL,
  `to_codex_id`   bigint      NOT NULL,
  `kind`         varchar(32)  NOT NULL COMMENT '怀疑/信任/敌对/亲属…',
  `note`         varchar(512) DEFAULT NULL,
  `since_chapter_id` bigint   DEFAULT NULL COMMENT '此关系自哪章成立',
  `until_chapter_id` bigint   DEFAULT NULL COMMENT '哪章终止，null=仍有效',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_from` (`work_id`, `from_codex_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-人物关系';

-- ── ① 故事内事实：编年 ────────────────────────────────────────────
CREATE TABLE `scribe_chronicle` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `work_id`     bigint       NOT NULL,
  -- 双时间轴：叙述序 ≠ 故事内时间（倒叙/插叙会错位，只有一条轴必然算错先后）
  `chapter_id`  bigint       DEFAULT NULL COMMENT '叙述坐标：在第几章被讲出来',
  `para_index`  int          DEFAULT NULL COMMENT '章内第几段，出处定位用',
  `story_time`  varchar(64)  DEFAULT NULL COMMENT '故事内时间的显示值，如「第4夜 寅时」「三年前」',
  `story_order` bigint       DEFAULT NULL COMMENT '故事内时间的排序键（自定义纪元的整数刻度）',
  `summary`     varchar(1024) NOT NULL COMMENT '发生了什么，一句话',
  `thread_kind` varchar(16)  DEFAULT NULL COMMENT 'null=普通事实 | SETUP=伏笔埋设 | PAYOFF=伏笔回收',
  `thread_key`  varchar(64)  DEFAULT NULL COMMENT '伏笔标识，SETUP 与 PAYOFF 靠它配对',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_story` (`work_id`, `story_order`),
  KEY `idx_work_chapter` (`work_id`, `chapter_id`),
  KEY `idx_thread` (`work_id`, `thread_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-故事编年';

-- 编年涉及哪些人/物，多对多
CREATE TABLE `scribe_chronicle_ref` (
  `chronicle_id` bigint NOT NULL,
  `codex_id`     bigint NOT NULL,
  PRIMARY KEY (`chronicle_id`, `codex_id`),
  KEY `idx_codex` (`codex_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-编年与设定的关联';

-- ── ② 台词语料 ────────────────────────────────────────────────────
CREATE TABLE `scribe_voice_sample` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `work_id`    bigint       NOT NULL,
  `codex_id`   bigint       NOT NULL COMMENT '说话的角色',
  `line`       varchar(1024) NOT NULL COMMENT '原话',
  `context`    varchar(256) DEFAULT NULL COMMENT '对谁说/什么情境',
  `chapter_id` bigint       DEFAULT NULL COMMENT '出处',
  `para_index` int          DEFAULT NULL,
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_codex` (`work_id`, `codex_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-台词样本（写对白时做 few-shot）';

-- ── ③ 创作对话 ────────────────────────────────────────────────────
CREATE TABLE `scribe_dialog` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `work_id`     bigint       DEFAULT NULL COMMENT 'null=通用对话；非空=关联作品，首条消息后锁定（见 7.6.2）',
  `user_id`     bigint       NOT NULL,
  `title`       varchar(128) DEFAULT NULL COMMENT '会话标题，可由首问自动生成',
  `conversation_id` varchar(64) DEFAULT NULL COMMENT '透传给 AgentEngine 的会话ID',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_update` (`user_id`, `update_time`),
  KEY `idx_work_update` (`work_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-创作对话会话';

CREATE TABLE `scribe_dialog_message` (
  `id`         bigint      NOT NULL AUTO_INCREMENT,
  `dialog_id`  bigint      NOT NULL,
  `role`       varchar(16) NOT NULL COMMENT 'user|assistant',
  `content`    mediumtext  NOT NULL,
  `tokens`     int         DEFAULT NULL,
  `status`     varchar(16) NOT NULL DEFAULT 'DONE' COMMENT 'DONE|STOPPED|FAILED，只有 DONE 进上下文（见 7.6.6）',
  -- 决策留痕：这条建议最后落到哪里去了
  `adopted`    tinyint(1)  NOT NULL DEFAULT 0,
  `adopted_ref` varchar(128) DEFAULT NULL COMMENT '落点，如 chapter:12#p4 / codex:8',
  `create_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dialog` (`dialog_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-创作对话消息';

-- ── AI 提议（未确认的一切都在这里）────────────────────────────────
CREATE TABLE `scribe_proposal` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `work_id`     bigint       NOT NULL,
  `user_id`     bigint       NOT NULL,
  `target_type` varchar(32)  NOT NULL COMMENT 'CODEX|CODEX_ATTR|CHRONICLE|VOICE|RELATION',
  `target_id`   bigint       DEFAULT NULL COMMENT 'null=新建，非空=修改既有',
  `op`          varchar(16)  NOT NULL COMMENT 'CREATE|UPDATE',
  `payload`     json         NOT NULL COMMENT '提议的新值（UPDATE 时前端据此渲染 diff）',
  `reason`      varchar(512) DEFAULT NULL COMMENT 'AI 给出的依据',
  `source_chapter_id` bigint DEFAULT NULL COMMENT '从哪一章抽出来的',
  `source_para_index` int    DEFAULT NULL,
  `status`      varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|ACCEPTED|REJECTED',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_status` (`work_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-AI提议（确认后才 apply 到业务表）';
```

> **`story_order` 为什么是整数而不是 datetime**：小说的故事时间常是「第四夜」「三年前」「大业九年」这类
> 自定义纪元，塞进 `datetime` 会被迫编造年月日。用整数刻度排序 + `story_time` 存显示值，
> 既能正确排序又不失真。刻度的粒度由作者自定（一夜=1 或一日=100，随书而定）。

---

## 五、AI 能力落点

### 5.1 三类能力，三条路径

| 能力 | 例子 | 形态 | 走哪条路 |
|---|---|---|---|
| **短平快改写** | 润色、扩写、缩写、续写 | 同步一次调用 | `AgentEngine.run()` FLOW，单节点 |
| **对话式创作** | 「柳三娘为什么报官？」 | 多轮 + 工具 | `AgentEngine.converse()` CONVERSATIONAL |
| **长任务** | 全书一致性检查、批量抽取设定 | 异步 + 回调 | `AgentEngine.run()` + webhook 落库 |

前两条对应 `ui-html/scribe-editor.html` 的三个入口；第三条对应「审校」tab 与设定页的「从正文重新抽取」。

> **`POST /scribe/ai/generate` 的实现**：前端已定义的 8 种 `AiAction` 不各配一张 flow，
> 而是**一张 flow + `action` 作为入参**选提示词（`ai_prompt` 的 `prompt_code = SCRIBE_{ACTION}`）。
> 理由：8 个 action 的差别只在提示词，编排结构完全相同，建 8 张图是纯冗余。

### 5.2 上下文装配：本设计的关键一环

生成质量取决于喂给模型什么。`AiGenerateRequest.codexIds` 只是用户显式勾选的部分，
真正的装配顺序（后者不覆盖前者，是叠加）：

```text
1. 本书 pinned 的设定           ← scribe_codex WHERE pinned=1（永远带上）
2. 用户显式勾选的 codexIds       ← 请求参数
3. 当前章节 synopsis + 前文若干段 ← precedingText
4. 与当前选区语义相关的设定/前文  ← knowledge_search(kb_code=work-{id})
5. 涉及角色的台词样本（写对白时）  ← scribe_voice_sample，top 8
6. 相关编年（近 N 条 + 未回收伏笔）← scribe_chronicle
```

第 4 步是唯一走向量的地方，也是 `ai_knowledge_base` 唯一的用武之地。
**1/2/5/6 全部是精确查询**，不经过任何语义检索——这正是不能用 `ai_memory` 的原因（C2）。

### 5.3 新增工具（`ToolDefinition` bean）

| tool_code | 作用 | scope |
|---|---|---|
| `codex_search` | 按名/别名/kind 精确查设定 | COPILOT_TOOL + FLOW_NODE |
| `codex_read` | 读一条设定的全部硬事实 | COPILOT_TOOL + FLOW_NODE |
| `chronicle_query` | 按章节/人物/伏笔状态查编年 | COPILOT_TOOL + FLOW_NODE |
| `propose_change` | **提交一条提议**（不落业务表，写 `scribe_proposal`） | COPILOT_TOOL |

> **为什么给 AI 的是 `propose_change` 而不是 `codex_update`**：
> 模型不该有直接改写设定库的权限。它只能往「待确认」队列里放东西，
> apply 的动作由用户在 UI 上做，走 scribe 自己的 Service。
> 这与 harness 里 `commit_draft` 需要 `DraftConfirmation` 是同一条安全律。

### 5.4 一致性检查（价值最高的一块）

`POST /scribe/ai/works/{id}/consistency-check` 是长任务，检查项分两类：

**规则可判的（不花 token，先跑）**：
- `scribe_chronicle` 中 `thread_kind=SETUP` 但无配对 `PAYOFF` 的 → 未回收伏笔
- `scribe_relation` 的 `since/until` 章节序颠倒
- `story_order` 与 `chapter.sort_order` 的偏序矛盾（非倒叙场景下）
- 正文提及某设定别名，但该设定 `first_seen_chapter_id` 在其后

**需要模型判的（前者过滤后再喂）**：
- 人物行为与 `locked=1` 的硬事实冲突
- 台词与该角色语感样本明显不符
- 前后描述矛盾（瞳色、身高这类）

> **顺序很重要**：规则先跑能砍掉大部分噪音，也让「全书检查」在长篇上仍然可负担。
> 结果统一落 `scribe_proposal`（`target_type` 复用）或独立的问题清单表，
> 前端渲染成 `ui-html/scribe-world.html` 里的「冲突」面板。

---

## 六、REST 契约

严格对齐前端已有的 `src/api/`：

```text
# 作品 / 章节（已在 work.ts 中定义）
GET    /scribe/works                       ?pageNum&pageSize&keyword&status&sort  → PageResult<WorkListItem>
POST   /scribe/works                       → WorkDetail
GET    /scribe/works/{id}                  → WorkDetail（含 volumes 与 chapters）
PUT    /scribe/works/{id}
GET    /scribe/chapters/{id}               → ChapterDetail（含正文）
PUT    /scribe/chapters/{id}               ChapterSaveRequest

# 设定库（已在 codex.ts 中定义）
GET    /scribe/works/{workId}/codex        ?kind → CodexEntry[]
POST   /scribe/works/{workId}/codex
PUT    /scribe/works/{workId}/codex/{id}
DELETE /scribe/works/{workId}/codex/{id}

# 本设计新增
GET    /scribe/works/{workId}/codex/{id}/attrs         → 硬事实列表
PUT    /scribe/works/{workId}/codex/{id}/attrs/{key}   → 改一条（locked 时拒绝 AI 来源）
GET    /scribe/works/{workId}/codex/{id}/voices        → 台词样本
POST   /scribe/works/{workId}/codex/{id}/voices        → 从正文摘录一条
GET    /scribe/works/{workId}/chronicle    ?chapterId&codexId&thread → 编年
POST   /scribe/works/{workId}/chronicle
GET    /scribe/works/{workId}/threads      → 伏笔清单（含未回收）
GET    /scribe/works/{workId}/proposals    ?status → 待确认提议
POST   /scribe/proposals/{id}/accept       → apply 到业务表
POST   /scribe/proposals/{id}/reject
GET    /scribe/dialogs                     ?keyword&workId → 创作对话列表（对话可不关联作品，故不挂在作品下）
POST   /scribe/dialogs/{id}/messages       → 发一轮（SSE 流式）
POST   /scribe/dialogs/messages/{id}/adopt → 标记采纳 + 落点
# 对话的完整接口与 SSE 事件约定见 7.6.5

# AI（已在 ai.ts 中定义）
POST   /scribe/ai/generate                 AiGenerateRequest → AiGenerateResult
POST   /scribe/ai/works/{id}/consistency-check   → 长任务，异步
POST   /scribe/ai/works/{id}/extract       → 从指定章节抽取设定/编年，产出 proposals

# 账号：复用 manager 现有端点，scribe 不实现登录注册（见第六·五章）
POST   /manager/auth/login                 已存在，在网关白名单
POST   /manager/auth/register              已存在
POST   /manager/auth/logout                已存在

# scribe 只负责「写作相关的账号周边」
GET    /scribe/account/pref                → scribe_user_pref
PUT    /scribe/account/pref
POST   /scribe/account/export              → 导出全部数据（Markdown + JSON）
DELETE /scribe/account/data                → 删除本人全部写作数据（含 Milvus 侧）
```

### 6.5 多用户与账号体系

形态是**各写各的**：每位作者独立账号，作品互不可见，本期不做协作与公开发布。

> **⚠️ 更正（本节曾设计 `scribe_user` 表，是错的）**
>
> 全仓**已有统一账号体系**，scribe 不该另起一套：
>
> - **`sys_user` 表已存在**（`script/nebula.sql:2509`）：`username` / `password`(BCrypt) /
>   `nickname` / `avatar` / `mobile` / `email` / `status` / `deleted`，且 email、mobile、username 三个唯一索引俱全。
> - **网关已做集中鉴权**：`AuthGlobalFilter`（order = -100）用 Sa-Token 校验登录，
>   然后把 `X-User-Id` / `X-User-Roles` / `X-User-Perms` **注入下游请求头**，
>   并且会先**剥掉外部传入的同名头**防伪造。
> - **登录注册端点已存在**：`/manager/auth/login`、`/manager/auth/register`、`/manager/auth/logout`、
>   `/manager/captcha/**`，都在网关白名单里。
>
> 再建一张 `scribe_user` 会导致两套账号、两套密码、两处注销——**这是纯粹的错误，不是取舍**。

**正确做法**：

1. **复用 `sys_user` 与 `/manager/auth/*`**。scribe 不实现登录注册，
   前端直接调用现有端点；「笔名」用 `sys_user.nickname`，头像用 `sys_user.avatar`。
2. **scribe 服务不解析 token**，只读网关注入的 `X-User-Id`。业务表里的 `user_id` 就是 `sys_user.id`（雪花 ID）。
3. **网关白名单需要新增**（否则 scribe 全部接口都要登录，落地页与静态资源会 401）：
   ```yaml
   whitelist:
     - /scribe/front/**     # 若将来做公开阅读页，沿用 blog/forge 的 front 前缀约定
   ```
   注意 `/scribe/**` 其余路径**一律不进白名单**——写作数据必须登录才能访问。
4. **仍需一张 scribe 侧的用户偏好表**（这部分不属于 `sys_user` 的职责）：
   ```sql
   CREATE TABLE `scribe_user_pref` (
     `user_id`       bigint      NOT NULL COMMENT '= sys_user.id，非自增',
     `bio`           varchar(256) DEFAULT NULL COMMENT '一句话简介',
     `default_style` varchar(32)  DEFAULT 'auto' COMMENT '默认文风预设',
     `editor_theme`  varchar(16)  DEFAULT 'auto' COMMENT 'light|dark|auto',
     `daily_goal`    int          DEFAULT NULL COMMENT '每日字数目标',
     `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
     `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     PRIMARY KEY (`user_id`)
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-用户偏好';
   ```

三条仍然成立的约定：

1. **归属校验在服务端，不靠前端隐藏入口。** 所有 `/scribe/**` 接口都要校验资源
   `user_id` 与 `X-User-Id` 一致——第四章所有业务表都已带 `user_id` 或可经 `work_id` 溯源。
   网关只证明「你是谁」，**不证明「这本书是你的」**，后者是 scribe 自己的责任。
2. **不泄露注册用户名单。** 登录/忘记密码的文案约定见 `/manager/auth`，scribe 沿用即可。
3. **注销即硬删除。** 首页向用户承诺了「注销即真正删除」。但 `sys_user` 用的是
   `deleted` 软删除——**这里有语义冲突**：注销时 `sys_user` 可软删，
   但 scribe 侧的作品、章节、设定、编年、对话与 KB 文档（含 Milvus）**必须真删**。
   若做不到，首页那句承诺就要改掉，不能留一句做不到的话。

> 前端 `src/stores/auth.ts` 目前只是把 `isLoggedIn` / `token` 存进 localStorage 的**本地桩**，
> 没有任何真实请求。接入时改为对接 `/manager/auth/*`。界面见 `ui-html/scribe-auth.html`
> （其中「注册/登录表单」可直接复用，但**提交目标是 manager 而非 scribe**）。

网关需新增一条路由：

```yaml
- id: nebula-service-scribe
  uri: http://${NEBULA_SERVICE_SCRIBE_HOST:127.0.0.1}:${NEBULA_SERVICE_SCRIBE_PORT:8085}
  predicates:
    - Path=/scribe/**
```

> 端口取 8085（8084 未占用但 forge 用了 8884，避开歧义）。

---

## 七、风险与取舍

| | 风险 | 对策 |
|---|---|---|
| **R1** | **Milvus 是新的运维依赖**，scribe 若强依赖向量检索，没部署 Milvus 就跑不起来 | 语义检索（5.2 第 4 步）**必须可降级**：`knowledge.enabled=false` 时跳过该步，其余五步全是 MySQL 精确查询，功能不残废只是召回变弱。**不允许把 Milvus 做成硬依赖** |
| **R2** | 正文投影进 KB 的**时机与成本**：每次保存都重新切块 embed，长篇会很贵 | `indexed_at` 标记 + 防抖：章节停止编辑 N 分钟后才投影；只投影变更章节，不整书重建 |
| **R3** | AI 抽取产生**大量低质提议**，用户被淹没 | 抽取时带置信度，低于阈值不入队；同一 `target_id + attr_key` 已有 PENDING 时合并而非追加；提供「全部忽略本章」 |
| **R4** | `locked` 只是业务约定，**模型仍可能在正文里写出矛盾内容** | locked 的语义是「一致性检查以它为准绳」，不是「模型写不出来」。检查环节兜底，UI 明示这一点 |
| **R5** | `story_order` 需要作者理解「刻度」概念，**心智负担** | UI 上不暴露整数，只让用户填「第几夜/三年前」，由前端按录入顺序生成刻度，允许拖拽重排 |
| **R6** | 长文本 `detail` 超 Milvus VarChar(8192)（C5） | 复用 `TextChunker`，与 KB 现有导入同一条路径，不特殊处理 |
| **R7** | scribe 直调 `AiService` 会**违反 Harness 硬收口** | scribe 的 Service 层只调 `AgentEngine`；`nebula-sdk-ai-archtest` 的 `@AnalyzeClasses` 需把 `com.nebula.scribe` 加进去 |

---

## 八、落地批次

> **顺序铁律：先能写字，再谈 AI。** 编辑器跑通之前做 AI 抽取没有意义——没有正文可抽。

| 阶段 | 交付 | 验收 |
|---|---|---|
| **0** 账号接入 | **不建用户表**：对接现有 `/manager/auth/*` 与 `sys_user`；scribe 读网关注入的 `X-User-Id`；`scribe_user_pref` 表；前端替换 `auth.ts` 本地桩 | 登录后带 token 访问 `/scribe/**` 全通；**换个账号登录看不到别人的作品**；伪造 `X-User-Id` 头**必须**被网关剥掉 |
| **1** 服务骨架 + 作品/章节 CRUD | 新建 `nebula-service-scribe`（端口 8085）；网关路由；`scribe_work`/`volume`/`chapter` 三表；对齐 `work.ts` 的 REST | 前端 `VITE_USE_MOCK=false` 后，作品列表页与新建作品**完全可用** |
| **2** 写作台前端 | `views/editor/`，按 `ui-html/scribe-editor.html` 实现；TipTap 编辑器；自动保存 | 能新建章节、写正文、切章节、字数统计正确 |
| **3** 设定库 CRUD | `scribe_codex` + `attr` + `relation` + `voice_sample`；对齐 `codex.ts`；按 `ui-html/scribe-world.html` 实现人物卡 | 手工建人物、加硬事实、锁定字段、摘录台词全通 |
| **4** AI 基础能力 | `POST /scribe/ai/generate`；一张 flow + 8 个 `ai_prompt`；5.2 的上下文装配（**先只做 1/2/3/5，跳过向量**） | 选中文字→润色→插入正文全链路通；生成时确实带上了 pinned 设定 |
| **5** 编年 + 伏笔 | `scribe_chronicle` + `ref`；伏笔配对查询；编年页 | 手工记事件、标伏笔、未回收清单正确 |
| **6** 提议机制 | `scribe_proposal` 表；`propose_change` 工具；`POST /ai/works/{id}/extract`；右栏待确认面板 | 跑一次抽取，产出 proposals；accept 后**确实**写进业务表且带出处 |
| **7** 一致性检查 | 规则检查（不花 token）先行；模型检查后置；冲突面板 | 故意制造一处硬事实矛盾，检查**必须**报出并能定位到段 |
| **8** 创作对话 | `scribe_dialog` 两表；`AgentEngine.converse()`；SSE；采纳留痕 | 多轮对话通；标记采纳后能跳到落点 |
| **9** 向量增强（可选） | 建 `kb_code=work-{id}`；章节与设定投影；5.2 第 4 步接入；`indexed_at` 防抖 | 开关关闭时功能不残废（R1 验收）；开启后长篇召回明显变好 |

**并行关系**：0 → 1 → 2 严格串行（没有账号就没有 `user_id`，后面所有表的归属都无从谈起）；
3 ‖ 5 可并行；4 依赖 1；6 依赖 3+5；7 依赖 6；8 独立可提前；9 最后且可选。

---

## 七·三、想法推进与思路提醒

> 界面见 `ui-html/scribe-ideas.html`。两件事：把零碎想法养成可写的东西；在对的时机提醒。

### 7.3.1 为什么现有的「找灵感」不够

`AI_ACTIONS` 里的 `brainstorm` 是**一次性**的——弹四条建议，采纳一条，其余消失。
`src/data/inspiration.ts` 里的提示卡是**静态**的，跟具体这本书无关。

但真实创作里，好想法常常是**先记下来、放几天、跟别的东西撞上、才突然可用**。
缺的是「想法的生命周期」：捕获 → 养 → 串联 → 变成可写的东西。

### 7.3.2 成熟度模型：四级

| 级别 | 含义 | 例子 |
|---|---|---|
| `SEED` 种子 | 一句闪念，没有上下文 | 「你数错了。」 |
| `CLUE` 线索 | 有了「谁 / 为什么」 | 「裴照是漏更司里唯一不想漏掉任何人的」 |
| `SCENE` 场景 | 有具体画面 | 「更夫死时一只鞋穿着一只在手上」 |
| `READY` 可写 | 能对应到某一章 | 「柳三娘的摊设在必经巷口，她在等他别回来」 |

**AI 的职责是追问，不是代写。** 用一两个具体问题把想法推进一级
（「这个细节缺一个『谁看见了』——沈砚先到还是官府先到？」），
问不动就先放着。等级由**用户确认**或**AI 判定 + 用户默认接受**推进，不自动跳级。

### 7.3.3 DDL

```sql
CREATE TABLE `scribe_idea` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `work_id`     bigint       NOT NULL,
  `user_id`     bigint       NOT NULL,
  `content`     mediumtext   NOT NULL COMMENT '想法正文（捕获时可能只有一句）',
  `kind`        varchar(16)  DEFAULT NULL COMMENT 'PLOT|CHARACTER|LINE|SETTING；捕获时可空，AI 事后归类',
  `maturity`    varchar(16)  NOT NULL DEFAULT 'SEED' COMMENT 'SEED|CLUE|SCENE|READY',
  `status`      varchar(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN|USED|DROPPED',
  `used_ref`    varchar(128) DEFAULT NULL COMMENT '用掉时的落点，如 chapter:12#p4',
  `target_chapter_id` bigint DEFAULT NULL COMMENT 'READY 时指向计划写入的章节',
  `last_nudged_at` datetime  DEFAULT NULL COMMENT '上次 AI 追问时间，控制打扰频率',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_maturity` (`work_id`, `maturity`, `status`),
  KEY `idx_work_update` (`work_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-想法收件箱';

-- 想法与设定/伏笔/章节的关联（AI 发现或用户手建）
CREATE TABLE `scribe_idea_link` (
  `idea_id`     bigint      NOT NULL,
  `target_type` varchar(16) NOT NULL COMMENT 'CODEX|CHRONICLE|CHAPTER|IDEA',
  `target_id`   bigint      NOT NULL,
  `relation`    varchar(16) NOT NULL DEFAULT 'RELATED' COMMENT 'RELATED|MERGED_FROM|RESOLVES',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`idea_id`, `target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-想法关联';

-- 提醒：统一收口，便于做频率控制与「不再提醒」
CREATE TABLE `scribe_reminder` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `work_id`     bigint       NOT NULL,
  `user_id`     bigint       NOT NULL,
  `kind`        varchar(24)  NOT NULL
      COMMENT 'THREAD_READY|SETTING_CONFLICT|IDEA_RELEVANT|CHAR_ABSENT|IDEA_STALE',
  `scope`       varchar(16)  NOT NULL DEFAULT 'INLINE' COMMENT 'RESUME|INLINE|REVIEW',
  `title`       varchar(256) NOT NULL,
  `detail`      varchar(1024) DEFAULT NULL,
  `anchor_chapter_id` bigint DEFAULT NULL COMMENT '相关章节，INLINE 提醒据此触发',
  `ref_type`    varchar(16)  DEFAULT NULL COMMENT '关联对象类型',
  `ref_id`      bigint       DEFAULT NULL,
  `status`      varchar(16)  NOT NULL DEFAULT 'PENDING'
      COMMENT 'PENDING|SHOWN|ACTED|DISMISSED|MUTED',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_work_scope_status` (`work_id`, `scope`, `status`),
  KEY `idx_anchor` (`anchor_chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-提醒';

-- 写作现场快照：残局提醒的数据来源
CREATE TABLE `scribe_session_mark` (
  `work_id`     bigint      NOT NULL COMMENT '一本书只留最近一次',
  `user_id`     bigint      NOT NULL,
  `chapter_id`  bigint      NOT NULL,
  `caret_para`  int         DEFAULT NULL COMMENT '光标所在段落序号',
  `tail_text`   varchar(1024) DEFAULT NULL COMMENT '停笔处最后几句原文（回到状态最快的东西）',
  `words_added` int         NOT NULL DEFAULT 0 COMMENT '当次新增字数',
  `intent`      varchar(512) DEFAULT NULL COMMENT '当时的未完成意图（取自梗概/对话）',
  `ended_at`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-上次停笔现场';
```

### 7.3.4 三类提醒的触发源

| 类型 | scope | 触发时机 | 数据来源 |
|---|---|---|---|
| **残局提醒** | `RESUME` | 打开作品时 | `scribe_session_mark` + 本章待确认草稿 + 章节梗概 |
| **实时提醒** | `INLINE` | 保存/段落间隙，按当前章节匹配 | 伏笔（`scribe_chronicle`）、锁定字段（`scribe_codex_attr`）、想法（`scribe_idea`） |
| **周期回顾** | `REVIEW` | **每十章**触发，不按周 | 全书统计 + 节奏曲线 + 冷线索 |

> **为什么回顾按章节数而不按周**：写作节奏因人而异，有人一周十章有人一月一章。
> 按时间触发会对慢写的人变成骚扰，对快写的人又太稀疏。按章节数贴合创作本身。

### 7.3.5 三条自律（这块最容易做砸）

1. **总量有上限。** 残局卡每次一张；实时提醒**同时最多一条**浮到正文，其余攒在侧栏；
   串联建议每天 2–3 条；回顾每十章一次。
   *提醒一旦变成噪音，用户会连同真正重要的「设定冲突」一起无视。*
2. **只提「你自己写过或定过的事」。** 伏笔是你埋的、设定是你锁的、想法是你记的。
   **不做主观写作指导**——「这里节奏偏慢」不配打断人，那是「挑毛病」按钮的职责，由用户主动触发。
   UI 上把这一项显式列为「不做」，避免后续有人加回来。
3. **可静音且记住。** `status=MUTED` 是终态，同一条提醒划掉后本章不再出现。

### 7.3.6 捕获必须零摩擦

底部一行输入框，**回车即存**，不选分类、不填标题、不选关联。
`kind` 与 `scribe_idea_link` 全部交给 AI 事后补。

> 理由很实在：灵感在填表的过程中就没了。捕获阶段任何一个必填项都是漏斗。

### 7.3.7 与其他模块的关系

- **想法 → 设定库**：`READY` 的想法可一键存成 `scribe_codex` 或 `scribe_chronicle`，走既有的提议—确认流。
- **想法 ← 审校报告**：审校发现「裴照已建档但从未出场」，可反向生成一条想法提示。
- **串联建议**：在 `scribe_idea` 内部做两两相关性匹配。**知识库可用时走向量**，
  不可用时退化为标签 + 关联对象重合度——与 R1 的降级原则一致。

### 7.3.8 落地批次

| 阶段 | 交付 | 验收 |
|---|---|---|
| **I1** 收件箱 | `scribe_idea`；零摩擦捕获；四级成熟度手动切换；列表与筛选 | 回车能存；能手动升级；筛选正确 |
| **I2** AI 追问与串联 | 追问提示词；`scribe_idea_link`；每日 2–3 条串联建议 | 追问确实具体（不是「你可以再想想」）；串联可合并成一条 |
| **I3** 残局提醒 | `scribe_session_mark`（保存时写）；打开作品的残局卡 | 隔天回来能看到最后三句原文与未完成意图 |
| **I4** 实时与回顾 | `scribe_reminder`；INLINE 触发与静音；每十章回顾 | 同时只浮一条；划掉不再来；第 10 章自动出回顾 |

**I1 → I2 串行**；I3 独立可提前（价值高、成本低，建议紧跟 I1）；I4 依赖 I1+I3。

---

## 七·四、职责边界：哪些界面在 scribe，哪些在 manager

> 本节是**已定决策**，用来防止后续把管理能力误塞进作者端。

**一句话原则：作者用的进 scribe，配置 AI 的留 manager。**

分界线是「谁在用」——scribe 面向**写小说的作者**，manager 面向**配置这套系统的人**（通常就是你自己）。
两者用户群、鉴权、迭代节奏都不同，界面不该混。

| 能力 | 落点 | 理由 |
|---|---|---|
| 书房 / 写作台 / 故事世界 / 大纲板 / 审校报告 | **scribe** | 作者的日常工作台 |
| 创作对话页（全屏，见 7.6） | **scribe** | 作者和 AI 聊情节、人物，是创作过程的一部分 |
| 角色音色配置 / 有声书合成 / 声纹克隆 | **scribe** | 属于「这本书怎么呈现」，是创作的一部分 |
| **流程编排画布（ai-flow）** | **manager（维持现状，不迁移）** | 见下 |
| Agent 定义、模型档案、提示词、工具、MCP、知识库 | **manager（维持现状）** | 同上，都是系统配置 |
| 音色库的**平台预置音色**管理 | **manager（新增）** | 运营维护 `scribe_voice` 中 `source=PRESET` 的行 |

### 为什么 ai-flow 不迁到 scribe

1. **普通作者不需要编排画布。** 作者要的是「点润色」，不是「拖一个 LLM 节点连到 IF 节点」。
   把画布摆到写作产品里，是把实现细节暴露给不关心它的人。
2. **manager 已经有完整的一套。** `views/` 下已有 `ai-flow` / `ai-agent` / `ai-knowledge` /
   `ai-prompt` / `ai-tool` / `ai-model-profile` / `ai-mcp-server`，路由（`router/routes/modules/ai-flow.ts`）
   与编辑器布局守卫（`guard.ts` 的 `FLOW_EDITOR_LAYOUT_KEY`）都已就位。**迁移是纯负债。**
3. **依赖不该带进 scribe。** 画布依赖 Element Plus + X6（500KB+）+ CodeLayout，
   为一个作者用不到的功能引入这些包不划算。
   （2026-09-24 更新：为做创作对话，scribe 已引入 Element Plus 与 Element Plus X，见 7.6.7；
   但 X6 与 CodeLayout 仍然没有理由进来，本条结论不变。）

> **`ui-html/scribe-flow.html` 的定位随之改变**：它不再是「移植稿」，而是
> **manager 端 ai-flow 若要做视觉改版时的参考稿**（AntD 五色 → 语义四档的配色方案仍然成立，
> 因为那套高饱和色在任何界面里都偏吵）。是否改版是独立决策，与 scribe 无关。

### scribe 与 manager 的连接点

scribe 不做编排界面，但**要用编排的产物**：

- scribe 的 `POST /scribe/ai/generate` 走 `AgentEngine`，跑的是 manager 里配好的 flow；
- 8 个 `SCRIBE_*` 提示词存在 `ai_prompt`，在 manager 的提示词页维护；
- 写作用的模型档案在 manager 的 `ai_model_profile` 页配。

**作者看不到这些，但它们是 scribe 的燃料。** 这也是为什么 scribe 只调 `AgentEngine`
而不碰 `AiService`（第七章 R7）——门面之下的东西都归 manager 管。

---

## 七·五、角色声音与有声书（TTS）

> 界面见 `ui-html/scribe-voice.html`。四块能力：角色音色配置、对白归属识别、
> 整章有声书合成、声纹克隆。

### 7.5.1 与「台词样本」的关系：一个概念的两面

`scribe_voice_sample`（第四章）存的是角色说过的**原话**，服务于**文本生成**——
让 AI 写对白时有语感可依。本章新增的是同一个「声音」概念的**听觉面**：
这个角色听起来是什么样。

**两者共用 `scribe_codex` 这一条人物记录，不是两套东西。** 人物卡的「声音」tab
上半部分是语感摘要与台词样本（已有），下半部分是音色与语气参数（新增）。

### 7.5.2 数据模型

```sql
-- 音色：平台预置 + 用户克隆，统一一张表
CREATE TABLE `scribe_voice` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `voice_code`    varchar(64)  NOT NULL COMMENT '业务编码，全局唯一',
  `name`          varchar(64)  NOT NULL COMMENT '展示名，如「青年男声·沉」',
  `description`   varchar(256) DEFAULT NULL,
  `gender`        varchar(16)  DEFAULT NULL COMMENT 'male|female|neutral',
  `age_range`     varchar(16)  DEFAULT NULL COMMENT 'child|youth|adult|elder',
  `source`        varchar(16)  NOT NULL DEFAULT 'PRESET' COMMENT 'PRESET=平台预置 | CLONED=用户克隆',
  `owner_user_id` bigint       DEFAULT NULL COMMENT 'CLONED 时归属用户；PRESET 为空',
  `provider`      varchar(32)  NOT NULL COMMENT '供应商编码（见 7.5.3）',
  `provider_voice_id` varchar(128) NOT NULL COMMENT '供应商侧的音色 ID',
  `sample_url`    varchar(512) DEFAULT NULL COMMENT '试听样本音频',
  `status`        tinyint(1)   NOT NULL DEFAULT 1,
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_voice_code` (`voice_code`),
  KEY `idx_owner` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-音色';

-- 角色 → 音色 + 语气参数（一个角色在一本书里只有一套配置）
CREATE TABLE `scribe_codex_voice` (
  `codex_id`    bigint      NOT NULL COMMENT '= scribe_codex.id；旁白用保留 id 0',
  `work_id`     bigint      NOT NULL,
  `voice_code`  varchar(64) NOT NULL,
  `speed`       decimal(3,2) NOT NULL DEFAULT 1.00 COMMENT '语速 0.50~2.00',
  `pitch`       int          NOT NULL DEFAULT 0 COMMENT '音高 -12~+12 半音',
  `pause_scale` decimal(3,2) NOT NULL DEFAULT 1.00 COMMENT '停顿倍率',
  `intensity`   decimal(3,2) NOT NULL DEFAULT 0.50 COMMENT '情感强度 0~1',
  `emotion`     varchar(16)  DEFAULT 'neutral' COMMENT '默认情绪，单句可覆盖',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`codex_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-角色音色配置';

-- 全书发音修正（人名/生僻字/多音字）
CREATE TABLE `scribe_lexicon` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `work_id`     bigint      NOT NULL,
  `word`        varchar(64) NOT NULL,
  `pronunciation` varchar(128) NOT NULL COMMENT '拼音（带声调）或 SSML phoneme',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_word` (`work_id`, `word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-发音词典';

-- 章节切分出的「句」：归属 + 合成产物，按句缓存
CREATE TABLE `scribe_audio_segment` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `work_id`     bigint       NOT NULL,
  `chapter_id`  bigint       NOT NULL,
  `seg_index`   int          NOT NULL COMMENT '章内顺序',
  `text`        varchar(2048) NOT NULL COMMENT '这一句的文本',
  `speaker_codex_id` bigint  DEFAULT NULL COMMENT '说话人；null=旁白',
  `attribution` varchar(16)  NOT NULL DEFAULT 'AUTO'
      COMMENT 'AUTO=AI判定 | CONFIRMED=用户确认 | UNSURE=待确认',
  `confidence`  decimal(3,2) DEFAULT NULL COMMENT 'AI 归属置信度',
  `emotion`     varchar(16)  DEFAULT NULL COMMENT '单句情绪覆盖',
  -- 合成产物：按 text+voice+参数 的哈希缓存，改一句只重合成一句
  `audio_url`   varchar(512) DEFAULT NULL,
  `duration_ms` int          DEFAULT NULL,
  `synth_hash`  varchar(64)  DEFAULT NULL COMMENT '合成输入指纹，变了才重合成',
  `status`      varchar(16)  NOT NULL DEFAULT 'PENDING'
      COMMENT 'PENDING|SYNTHESIZING|DONE|FAILED',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chapter_seg` (`chapter_id`, `seg_index`),
  KEY `idx_work_status` (`work_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-有声书句段';

-- 声纹克隆授权记录（合规要件，必须可追溯）
CREATE TABLE `scribe_voice_consent` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `voice_code`  varchar(64)  NOT NULL,
  `user_id`     bigint       NOT NULL,
  `consent_text_version` varchar(32) NOT NULL COMMENT '当时的协议版本号',
  `checked_items` json       NOT NULL COMMENT '逐项勾选记录',
  `ip`          varchar(64)  DEFAULT NULL,
  `user_agent`  varchar(256) DEFAULT NULL,
  `consented_at` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_at`  datetime     DEFAULT NULL COMMENT '撤回/删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_voice` (`voice_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-声纹克隆授权记录';
```

### 7.5.3 供应商抽象：不绑死任何一家

TTS 供应商的能力与计价差异很大（是否支持克隆、是否支持 SSML、按字符还是按秒计费），
且国内外可用性不同。**必须做一层 SPI**，与 `nebula-sdk-ai` 的 `AiProvider` 同构：

```java
public interface TtsProvider {
    String code();                                   // "aliyun" / "azure" / "minimax" ...
    boolean supportsCloning();
    byte[] synthesize(TtsRequest request);           // 文本 + voiceId + 参数 → 音频
    String cloneVoice(List<byte[]> samples, String name);   // 不支持则抛 UnsupportedOperationException
    void deleteVoice(String providerVoiceId);        // 用户删除克隆音色时必须真删到供应商侧
}
```

> **`deleteVoice` 不是可选项。** 用户点「删除并销毁声纹」时，只删本地行而把声纹留在
> 供应商那里，等于承诺没兑现——这与第六·五章「注销即硬删除」是同一条律。

### 7.5.4 对白归属识别

这是有声书里最难也最关键的一步。中文小说大量对白不带「XX说」，靠上下文判断。

**流程**：切句 → AI 判定说话人 → 低置信度标 `UNSURE` → **用户确认后才合成**。

三条实现约定：

1. **候选集优先取本章出场角色**。用 `scribe_chronicle_ref`（第四章）查本章涉及的
   `codex_id`，比全书角色列表准得多，也省 token。
2. **置信度阈值以下一律标 UNSURE**，不猜。念错说话人比念得不好听严重得多——
   前者会让听者直接出戏。
3. **用户确认过的归属不可被后续 AI 重跑覆盖**（`attribution=CONFIRMED` 是终态）。

### 7.5.5 按句缓存：改一句不重跑整章

`synth_hash = hash(text + voice_code + speed + pitch + emotion + lexicon_version)`。
合成前比对，未变则复用既有 `audio_url`。

意义很直接：一章 3,000 字改了一个错别字，不该重新烧 3,000 字符的额度。
这也让「换某角色音色」变成只重合成该角色的句子。

### 7.5.6 成本：与文本生成分开计

**TTS 按字符计费，文本生成按 token 计费，两者不是一回事**，配额表要分列
（在 8.5.2 的 `scribe_ai_quota` 上加 `tts_char_used` / `tts_char_limit`）。

一本 30 万字的书全量合成 = 30 万字符，**这是单次可能最贵的操作**。
所以：合成前必须显示预估消耗并二次确认；默认按章合成，不提供「一键全书」的裸入口。

### 7.5.7 合规红线

声纹是生物识别信息。国内涉及《民法典》第 1023 条（声音权参照肖像权保护）
与《生成式AI服务管理暂行办法》。产品上的处理：

| 约定 | 理由 |
|---|---|
| **授权界面在录音之前**，不是之后 | 事后勾选没有法律意义 |
| 三项**逐条勾选，不给「全选」** | 逐项确认才构成有效告知 |
| **只允许克隆本人声音**，产品上不做「上传他人录音」 | 授权链无法验证，直接不做以避免纠纷 |
| 授权记录**独立存表**并留存 IP/UA/协议版本 | 出事时要能证明用户确实同意过 |
| 删除时**级联删到供应商侧** | 见 7.5.3 |
| 合成音频**建议加不可听水印**（若供应商支持） | 生成内容可识别要求 |

> 与 8.5.7 的 AI 生成内容合规是同一件事的两个面，上线前一并给结论。

### 7.5.8 落地批次（接第八章之后）

| 阶段 | 交付 | 验收 |
|---|---|---|
| **V1** 音色与试听 | `TtsProvider` SPI + 一家实现；`scribe_voice`/`scribe_codex_voice`/`scribe_lexicon`；人物卡「音色」区 | 给沈砚配音色、调参数、用他的真实台词试听 |
| **V2** 切句与归属 | `scribe_audio_segment`；切句算法；AI 归属 + `UNSURE` 确认流 | 一章切句正确；不确定的能标出并确认；确认不被覆盖 |
| **V3** 合成与播放 | 按句合成 + `synth_hash` 缓存；进度；播放器；导出 MP3 | 改一句只重合成一句；导出可播放 |
| **V4** 声纹克隆 | 授权流 + 录音 + `scribe_voice_consent`；删除级联到供应商 | 未勾选授权无法进入录音；删除后供应商侧确实无残留 |

**V4 依赖 V1**（克隆产出的就是一个 `scribe_voice` 行）；V2 → V3 严格串行。

---

## 七·六、创作对话页（全屏）

> 2026-09-24 定稿。界面原型见 `ui-html/scribe-chat.html`（可直接用浏览器打开）。
>
> **三条已定决策：**
> 1. **独立全屏页**，不套页头页脚（与写作台 `/editor` 同一姿势）；
> 2. **对话可选绑定作品**——不绑是通用写作问答，绑了 AI 才能读这本书的设定与章节；
> 3. **两栏布局**：左侧会话列表 + 右侧对话区。上下文面板（第三栏）推迟，不进首批。

### 7.6.1 定位与入口

它是第三章 ③「我与 AI 的对话」的承载界面，对应 5.1 的**对话式创作**（`AgentEngine.converse()`），
与写作台里的 AI 面板分工明确：

| | 写作台 AI 面板 | 创作对话页 |
|---|---|---|
| 形态 | 选中文字 → 润色/扩写/续写，一问一答 | 多轮来回，可以聊很久 |
| 接口 | `POST /scribe/ai/generate` | `POST /scribe/dialogs/{id}/messages`（SSE） |
| 产物 | 直接进正文（经用户确认） | 对话记录；有价值的建议可「采纳」并记录落点 |

**路由**：`/chat`（新对话）、`/chat/:dialogId`（已有会话），需要登录，不在 `DefaultLayout` 里。

**入口**（都只是「带参数打开对话页」，不各自实现对话）：

| 入口 | 跳转 | 效果 |
|---|---|---|
| 首页「问问 AI 写作助手」卡片 | `/chat?q=问题` | 新建通用对话并自动发出第一问 |
| 作品详情页「和 AI 聊这本书」 | `/chat?workId=12` | 新建对话，预先关联这本书 |
| 写作台（批次 2 之后） | `/chat?workId=12&chapterId=34` | 同上，并把当前章节作为首轮上下文 |

参数用完即从地址栏清掉（`router.replace`），刷新页面不会重复提问。

### 7.6.2 对话与作品：可选绑定

`scribe_dialog.work_id` 由**必填改为可空**（见 4.2）：

- `work_id = null`：通用写作问答。上下文只有对话本身，不装配任何设定。
- `work_id` 非空：每一轮按 5.2 的装配链注入这本书的上下文（pinned 设定、相关编年、台词样本等）。

**关联只能在发出第一条消息之前选择或更换，之后锁定。** 想聊另一本书就开新对话。
理由：中途换书会让前半段对话的上下文与后半段不一致，模型会把 A 书的人物带进 B 书；
而为「换书」设计分割线、上下文截断，复杂度与收益不成比例。

归属校验照 6.5：`work_id` 必须属于当前用户，否则按「作品不存在」拒绝。

### 7.6.3 布局

```text
┌────────────────┬──────────────────────────────────────────────────┐
│ ← Scribe    «  │ 《断刀记》 ·  柳三娘为什么报官                    ✎ │  ← 顶栏：关联作品 + 标题
│ [ + 新对话 ]    │──────────────────────────────────────────────────│
│ 🔍 搜索对话     │                                                  │
│                │                       柳三娘为什么要去报官？  (我) │
│ 今天            │                                                  │
│ ▸ 柳三娘为什么… │  (AI) 从第二章她的说辞看，有三种可能……            │
│   《断刀记》    │       [复制] [重新生成]                           │
│   开头怎么写    │                                                  │
│ 最近 7 天       │                                                  │
│   卡文了怎么办  │   ┌──────────────────────────────────────────┐   │
│                │   │ 继续问……  Enter 发送 · Shift+Enter 换行  ➤ │   │  ← 输入区
│ 沈砚     ☀ ⋯   │   └──────────────────────────────────────────┘   │
└────────────────┴──────────────────────────────────────────────────┘
   16rem，可收起               消息列居中，最大宽度 48rem
```

**左栏（会话列表）**

- 顶部：返回 Scribe、收起侧栏、「新对话」按钮、按标题搜索。
- 列表按 **今天 / 昨天 / 最近 7 天 / 更早** 分组，按最后活动时间倒序；
  每项显示标题，关联了作品的在标题下显示书名小字。
- 悬停出现「⋯」：重命名、删除（二次确认）。
- 底部：当前用户昵称、主题切换（浅色 / 深色 / 纸张）。

**右栏（对话区）**

- 顶栏：关联作品选择器 + 对话标题（点击可改名）。
  作品选择器在**首条消息发出前**是下拉框（含「不关联作品」），之后变成只读标签。
- 消息区：用户消息在右，AI 回复在左，AI 回复按 Markdown 渲染。
  AI 消息下方的操作：复制、重新生成（仅最后一条）、采纳（仅关联了作品时，批次 C5）。
- 输入区：固定在底部，最多 2000 字；生成中发送按钮变为「停止」。

**窄屏（< 768px）**：左栏变为从左侧滑出的抽屉，顶栏左侧出现菜单按钮；消息区占满宽度。

### 7.6.4 状态与交互规则

| 场景 | 行为 |
|---|---|
| 空会话 | 欢迎语 + 4 个推荐问题。关联了作品时换成针对这本书的问题（「帮我梳理主要人物关系」等） |
| 点「新对话」 | **不落库**，只清空右栏并跳到 `/chat`；发出首条消息时才创建会话，避免空会话堆积 |
| 发出首条消息 | 创建会话 → 地址替换为 `/chat/:id` → 列表顶部出现该会话 |
| 默认标题 | 取首问前 20 个字；用户可随时改名（后续可选：由 AI 生成标题） |
| 生成中 | 流式逐字显示；输入框可继续打字但不能发送；发送按钮变「停止」 |
| 点「停止」 | 断开 SSE，已生成的部分保留，消息标记为「已停止」 |
| 回复失败 | 气泡内显示失败原因 + 「重试」按钮；失败的轮次不作为后续上下文 |
| 重新生成 | 只对最后一条 AI 回复开放；旧回复被替换，不保留多版本（首批） |
| 删除会话 | 二次确认后**硬删**（对话不是作品，不进回收站）；删除当前会话后回到 `/chat` |
| 打开历史会话 | 先加载最近 30 条，向上滚动再分页加载更早的消息 |
| 未登录 | 路由守卫拦到登录页，登录后回到原地址（含 `?q=`） |

### 7.6.5 接口

替换第六章里对话相关的三行（`/scribe/works/{workId}/dialogs` 改为不挂在作品下，因为对话可以不关联作品）：

```text
GET    /scribe/dialogs                 ?keyword&workId&pageNum&pageSize → PageResult<DialogListItem>
POST   /scribe/dialogs                 { workId?, title? }              → DialogDetail
PUT    /scribe/dialogs/{id}            { title, workId? }               → DialogDetail
                                        （workId 仅在会话还没有消息时允许修改，否则 409）
GET    /scribe/dialogs/{id}                                             → DialogDetail（含 messageCount，前端据此判断关联作品是否已锁定）
DELETE /scribe/dialogs/{id}                                             → 硬删会话与全部消息
GET    /scribe/dialogs/{id}/messages   ?beforeId&size                   → 按 id 倒序分页的历史
POST   /scribe/dialogs/{id}/messages   { content }                      → text/event-stream
POST   /scribe/dialogs/{id}/messages/regenerate                         → text/event-stream，替换最后一条 AI 回复
POST   /scribe/dialogs/messages/{id}/adopt  { ref }                     → 批次 C5
```

**SSE 事件约定**：

| event | data | 说明 |
|---|---|---|
| `meta` | `{ userMessageId, assistantMessageId }` | 第一帧，前端据此替换临时 id |
| `delta` | `{ text }` | 增量文本 |
| `done` | `{ tokens }` | 正常结束 |
| `error` | `{ code, message }` | 失败；已写入的部分保留，消息状态记为 FAILED |

停止由前端直接断开连接实现，服务端感知断开后把消息状态记为 STOPPED。
**每一轮都计入 8.5.2 的 AI 配额**，配额不足时在 `meta` 之前直接返回 `error`。

### 7.6.6 数据表改动

在 4.2 的基础上：

- `scribe_dialog.work_id`：`NOT NULL` → `DEFAULT NULL`；新增索引 `(user_id, update_time)` 支撑左栏列表。
- `scribe_dialog_message` 新增 `status varchar(16) NOT NULL DEFAULT 'DONE'`，取值 `DONE|STOPPED|FAILED`；
  组装上下文时只取 `DONE` 的轮次。
- 会话的 `update_time` 在每条消息写入时刷新，作为左栏排序依据。

### 7.6.7 组件选型

scribe 已引入 **Element Plus X**（2026-09-24，见第九章第 17 条），本页直接用它的对话组件：

| 区域 | 组件 |
|---|---|
| 左栏会话列表（分组、悬停菜单） | `Conversations` |
| 消息列表（自动滚动、回到底部按钮） | `BubbleList` + `Bubble` |
| AI 回复的 Markdown 渲染 | `md-editor-v3` 的 `MdPreview`（放进 `Bubble` 的 `content` 插槽） |
| 空会话 | `Welcome` + `Prompts` |
| 输入区 | `XSender` |

配色经 `styles/element-bridge.scss` 映射到 scribe 的设计令牌，跟随三套主题，不单独维护 Element Plus 暗色。

### 7.6.8 本期不做

附件与图片、语音输入、多模型切换、分享对话、对话内 `@设定` 引用（留到 C4 之后，
届时 `XSender` 的 mention 能力可直接用）、右侧上下文面板。

### 7.6.9 落地批次

| 批次 | 交付 | 验收 |
|---|---|---|
| **C1** 全屏页 + mock | 路由 `/chat`、两栏布局、会话列表与消息均为本地 mock；首页入口改跳 `/chat` | 新建、切换、重命名、删除会话，发消息收到演示回复，窄屏抽屉可用 |
| **C2** 会话落库 | `scribe_dialog` 两表（按 7.6.6）；会话 REST；消息接口先做非流式 | 刷新页面会话与消息仍在；换账号看不到别人的会话 |
| **C3** 流式 + 停止 | SSE（7.6.5 事件约定）；停止与失败状态 | 逐字输出；中途停止后内容保留且标记「已停止」 |
| **C4** 关联作品 | 作品选择器；按 5.2 装配上下文；首条消息后锁定 | 关联作品后，AI 回答能引用 pinned 设定里的事实 |
| **C5** 采纳留痕 | adopt 接口；AI 消息「采纳」操作与落点跳转 | 采纳后能从消息跳到对应章节段落 |

C1 纯前端，可立即开始；C2 起依赖后端；C4 依赖第八章批次 3（设定库）。

---

## 八·五、尚未考虑到的问题（本轮补充）

> 前面几章把「写什么、存哪里、怎么调 AI」讲清楚了。但一个真会被人用的写作产品，
> 下面这些**没想清楚就会返工或出事**。按「不解决就会出事」的程度排序。

### 8.5.1 编辑器选型与现有依赖冲突 ⚠️ 阻塞

`ui/nebula-scribe/package.json` 里已有的是 **`md-editor-v3`**（Markdown 编辑器），
但本设计的写作台原型依赖三件事：**选区悬浮工具条、AI 草稿的待确认装饰层、正文里设定词高亮**。

Markdown 编辑器做不了这些——它的内容模型是纯文本字符串，没有富文本的 range 装饰能力。

| 方案 | 代价 |
|---|---|
| **A. 换 TipTap（ProseMirror）** | 装饰层/选区/协同都原生支持，是小说编辑器的正解；但要弃用 `md-editor-v3`，且正文存储从 Markdown 变 JSON/HTML |
| **B. 留 Markdown，砍交互** | 悬浮条退化成「工具栏按钮」，AI 草稿只能整段替换不能行内标记；实现快但体验差一档 |
| **C. 自研 contenteditable** | 不要做。中文输入法 + undo 栈 + 选区，坑深不见底 |

> **建议 A**，且**必须在批次 2 之前定**——正文存储格式定错，后面所有章节数据都要迁移。
> 若选 A，第四章 `scribe_chapter.content` 的注释要从「正文」改为「正文（ProseMirror JSON）」，
> 并额外存一份 `content_text` 纯文本用于字数统计、全文检索与 KB 投影。

### 8.5.2 AI 成本没有任何闸门 ⚠️ 会出事

**全仓 grep 不到任何 quota / usage / billing / credit 表。** 而本设计里：

- 「全书一致性检查」要读 12 万字 → 单次可能烧 10 万+ token；
- 「从正文重新抽取」是全书级操作；
- 写作台的续写是高频操作。

一个用户点十次「全书检查」就能把 API 账单打穿，**目前没有任何东西拦得住**。
我在 `ui-html/scribe-auth.html` 的账号设置里画了「本月 AI 用量」进度条，
但那**只是 UI，后端没有对应实现**——这是我上一轮该说而没说清楚的。

必须补：

```sql
CREATE TABLE `scribe_ai_quota` (
  `user_id`        bigint      NOT NULL COMMENT '= sys_user.id',
  `period`         varchar(7)  NOT NULL COMMENT '账期 YYYY-MM',
  `gen_used`       int         NOT NULL DEFAULT 0 COMMENT '已用生成次数',
  `gen_limit`      int         NOT NULL DEFAULT 1000,
  `check_used`     int         NOT NULL DEFAULT 0 COMMENT '已用全书检查次数',
  `check_limit`    int         NOT NULL DEFAULT 10,
  `token_used`     bigint      NOT NULL DEFAULT 0 COMMENT '累计 token，用于成本核算',
  `update_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='写作-AI用量与配额';
```

三条实现约定：

1. **扣减在调用前，不在回调里**。先占额度再发请求，失败再退还——反过来会被并发打穿。
2. **长任务要预估**：一致性检查按「字数 ÷ 1000」预扣，跑完按实际结算。
3. **超额降级而非报错**：额度用尽时写作功能照常，只有 AI 按钮变灰并提示——
   *绝不能让用户因为额度问题写不了字*。

> `AgentHarness设计.md` 第八章的 `budget_config`（`maxTokens`/`maxToolCalls`/`maxWallClockMs`）
> 是 **Agent 单次运行**的护栏，与这里的**用户月度配额**是两层，都要有，互不替代。

### 8.5.3 自动保存的冲突与丢稿

写作产品**丢稿等于事故**。当前设计只写了「自动保存」三个字，太轻了。

- **多标签页**：同一章在两个标签页打开，A 页保存覆盖 B 页 → 用 `scribe_chapter` 加
  `revision bigint`，保存时 CAS，冲突则提示「此章已在别处修改」并给出对比。
  （这套 CAS 在 `nebula-sdk-ai-harness` 的 `DraftStore.compareAndSet` 已有现成写法可抄。）
- **离线/断网**：写了十分钟断网，保存全失败 → 前端 IndexedDB 落本地队列，恢复后重放。
- **本地快照**：即使服务端挂了，正文也该在浏览器里存得住。
- **版本历史**：第九章「待定」里我把它推后了，但**改废一章想撤销**是真实高频需求。
  最小实现：每次状态流转（草稿→修订→定稿）自动存一个快照，不做逐字历史。

### 8.5.4 存量作品导入

新用户手里往往已经有 30 万字的 Word/Markdown。**没有导入 = 老作者用不了**，
而老作者恰恰是这产品最该服务的人。

至少要有：`.md` / `.txt` 批量导入，按「第 N 章」正则切分成章节，导入后跑一次
「抽取设定」把人物地点提出来变成 proposals。这也是设定库冷启动最自然的入口——
否则新用户面对空空的设定库，得手填几十条才能享受到 AI 的好处。

### 8.5.5 导出的真实形态

首页承诺了「随时导出 Markdown，走得干净」，但没定义清楚。作者真正需要的导出有三种：

| 场景 | 格式 |
|---|---|
| 备份 / 迁移 | 全量 ZIP：Markdown 正文 + 设定 JSON + 编年 JSON |
| 投稿 / 给编辑看 | 单文件 DOCX 或 PDF，带章节分页，**不含任何设定与 AI 痕迹** |
| 发布到连载站 | 逐章纯文本，去掉 Markdown 标记 |

第二种最容易被忽略，但它是「写完之后」的唯一出口。

### 8.5.6 移动端的定位

三份原型都是 ≥1100px 的桌面布局，窄屏直接隐藏左右栏。这是对的——**长篇写作是桌面场景**。
但需要明确一件事：手机上**至少要能读和改错别字**。作者在地铁上想起某处要改，
打不开是很挫败的。建议移动端只做「只读 + 轻量编辑 + 看设定」，不做 AI 与大纲板。

### 8.5.7 AI 生成内容的合规

面向多用户的 AI 写作产品，国内合规上绕不开：

- **内容安全**：模型可能生成违规内容。至少要在 `AiService` 的过滤器链上挂一道检测，
  出问题时是**平台**担责，不是用户。
- **生成内容标识**：按《生成式AI服务管理暂行办法》，AI 生成内容需可识别。
  好在本设计的 `scribe_proposal` 与「AI 草稿待确认态」天然记录了哪些内容来自 AI，
  导出时可选择是否标注。
- **未成年人 / 实名**：若开放公网注册需要考虑，内部使用可跳过。

> 这一条不影响架构，但**上线前必须有结论**，且结论可能反过来要求加字段（如
> `scribe_chapter.ai_ratio`），所以现在就该记下来。

### 8.5.8 其余待明确的小项

| 项 | 说明 |
|---|---|
| **回收站** | 删章节/删作品是否可恢复？建议软删 30 天，与「注销硬删」不矛盾（前者是误删保护） |
| **章节排序** | `sort_order` 用整数在中间插入会全表重排，建议用间隔 1000 的稀疏值或 LexoRank |
| **字数统计口径** | 中文按字符数，是否含标点、空格、Markdown 标记？必须统一，否则进度条不可信 |
| **并发生成** | 同一用户连点五次「续写」，是排队、取消前一个、还是都跑？建议同章节同时只允许一个 |
| **KB 投影时机** | R2 提到防抖，但「章节删除后 Milvus 残留」也要处理，复用 `need_reindex` 对账思路 |
| **`ai_prompt` 冷启动** | 8 个 `SCRIBE_*` 提示词是种子数据，要随 DDL 一起给 INSERT 脚本 |
| **示例作品** | 新用户注册后给一本可删的示例书（就用「长安漏更司」），比空状态引导有效得多 |

---

## 九、决策记录

1. **scribe 是独立服务，不塞进 manager。** manager 是后台管理，scribe 是面向作者的生产工具，
   用户群、鉴权、迭代节奏都不同。blog 是先例。
2. **人物设定不进 `ai_memory`。** C1/C2/C3 三条硬约束，任意一条足以否决。
   `ai_memory` 是模糊召回的旁路，人物设定是必须逐字正确的主干。
3. **硬事实拆成 `scribe_codex_attr` 行，而非塞 `detail` 文本。** 要精确改写、要锁定、要 diff、
   要被一致性检查逐条比对——这四件事文本 blob 都做不了。
4. **台词样本单独建表。** 它是 few-shot 语料不是资料，「性格：冷淡」四个字对模型没有约束力。
5. **双时间轴。** 叙述序与故事内时间必须分开，否则倒叙一写就算错先后。
   `story_order` 用整数刻度而非 datetime，避免为「第四夜」编造年月日。
6. **AI 只能 `propose_change`，不给写权限。** 与 harness 的 `commit_draft` + 确认同一条安全律。
7. **8 个 AiAction 共用一张 flow，靠 `ai_prompt` 分流。** 结构相同只是提示词不同，建 8 张图是冗余。
8. **向量检索是增强不是地基。** R1：Milvus 不可用时功能必须仍然可用，只是召回变弱。
9. **一致性检查规则先行、模型后置。** 省 token，也让长篇可负担。

10. **不建 `scribe_user`，复用 `sys_user` + 网关 Sa-Token 鉴权。**（本轮更正，见 6.5）
    全仓已有统一账号体系与 `AuthGlobalFilter`，再造一套会导致两套密码、两处注销。
11. **AI 配额必须在批次 0/4 之间落地，不能推到最后。**（见 8.5.2）
    一个用户点十次全书检查就能打穿账单，这不是优化项而是止损项。
12. **编辑器选型在批次 2 之前定死。**（见 8.5.1）
    现有 `md-editor-v3` 撑不起悬浮条与装饰层；正文存储格式定错则全量迁移。
13. **流程编排画布留在 manager，不迁 scribe。**（本轮确认，见 7.4）
    作者不需要编排画布；manager 已有完整一套（含路由与布局守卫），迁移是纯负债；
    且能避免把 X6（500KB+）+ CodeLayout 引入 scribe。
    `ui-html/scribe-flow.html` 转为 manager 端视觉改版的参考稿。
14. **平台预置音色的管理页放 manager，作者端只做选用。**（见 7.4 / 7.5.2）
    `scribe_voice` 中 `source=PRESET` 的行由运营维护，`source=CLONED` 的归用户自己。
15. **想法有生命周期，不是一次性建议。**（见 7.3）
    四级成熟度 SEED→CLUE→SCENE→READY；AI 的职责是**追问**不是代写；捕获必须零摩擦（回车即存）。
16. **提醒只提「你自己写过或定过的事」，不做主观写作指导。**（见 7.3.5）
    并且总量有硬上限——提醒变噪音后，连真正重要的设定冲突也会被一起无视。
17. **创作对话是独立全屏页，对话可选绑定作品。**（2026-09-24，见 7.6）
    `scribe_dialog.work_id` 改为可空：首页「随便问问」不必先选书；关联作品只能在首条消息前选定，
    之后锁定，避免前后两本书的上下文混在同一段对话里。对话 UI 用 Element Plus X，
    经 `element-bridge.scss` 跟随 scribe 的三套主题。
18. **章节正文先存纯文本，字数按「去空白后的字符数」统计。**（2026-09-24，落实第 12 条）
    写作台第一版用 textarea，段落以换行分隔；换 TipTap 时纯文本可无损转成段落节点，迁移成本最低，
    等做 AI 悬浮条时再引入。字数口径：汉字、标点、字母、数字各算 1，空格换行不算，按码点计；
    前后端各一份实现（`WordCounter` / `countWords`）必须保持一致。
    `scribe_chapter` 同步落地了三处：`volume_id` 暂可空（卷表下一步再建）、
    `revision` 修订号做保存 CAS（见 8.5.3，冲突返回 409）、`sort_order` 按 1000 间隔稀疏分配（见 8.5.8）；
    `indexed_at` 推迟到 AI 批次再加。

### 待定（需在对应批次前给出结论）

- **编辑器**：~~TipTap 还是留 Markdown~~ 已定为纯文本起步（第 18 条）；TipTap 在做 AI 悬浮条时再引入。
- **合规**：内容安全过滤与 AI 生成标识——**阻塞上线**，可能反向要求加字段。
- **移动端**：确认只做「只读 + 轻量编辑」，还是完全不做。
- 章节正文的版本历史——最小实现（状态流转时存快照）建议进批次 2，不必做逐字历史。
- 多人协作 / 共享世界观——本期不做，`user_id` 已在表上预留。
- 长任务的进度可视化——依赖 `AgentEventSink` 的 SSE 落地（见 [AgentHarness设计.md](AgentHarness设计.md) 批次 2）。

---

## 关联文档

- [AgentHarness设计.md](AgentHarness设计.md) —— `AgentEngine` 门面、双形态、事件模型、工具治理。**scribe 的所有 AI 调用都从这里进。**
- [智能体设计.md](智能体设计.md) —— `ai_flow` / `ai_agent` 三表、Memory 生命周期。本文档第二章的 C2/C3 出自其 Import/Export 实现。
- [向量检索基建（Milvus）设计.md](向量检索基建（Milvus）设计.md) —— `ai_knowledge_base` 三表与 chunk→embed→search 管线，第四章的 KB 层复用它。
- [编排回调Webhook设计.md](编排回调Webhook设计.md) —— 长任务产物回调落库，第五章的第三条路径同姿势。
