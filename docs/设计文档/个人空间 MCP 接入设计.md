# nebula 个人空间 MCP 接入设计 v1

> 本文档记录 2026-10 落地的一批改动：**让 Claude Code 等 MCP 客户端凭一把 API Key 读写个人空间**，主要用途是在 Claude Code 里整理当天的工作内容、写日报。
>
> **核心结论先说：**
>
> 1. 个人空间（space 服务）内置一个 MCP 端点 `POST /space/me/mcp`（Streamable HTTP，无状态），**对 API Key 只开放这一个地址**，`/me/**` 下的 REST 接口一个都没开放。
> 2. 每个工具单独要求一个授权范围：读、写日报、写任务和笔记分开。没有删除类工具。
> 3. 协议部分用官方 MCP Java SDK（2.0.0，Jackson 3），但不用它自带的 Servlet，请求仍走 Spring MVC，API Key 守卫照常生效。
>
> API Key 本身的机制见 [API Key（SK）接入设计](API%20Key（SK）接入设计.md)。

---

## 一、怎么用

**1. 执行 SQL**：`script/mysql/space_mcp.sql`，加三个权限码（可重复执行）。超级管理员靠通配符天然拥有，其余角色按需在「角色管理」里勾选。

**2. 签发 key**：后台 →「系统管理」→「API 密钥」→「新增」，按用途勾选授权范围：

| 授权范围 | 能做什么 |
|---|---|
| `space:me:read` | 读任务、清单、会议、笔记、专注、日报周报、搜索 |
| `space:report:write` | 写日报 / 周报 |
| `space:me:write` | 新建、修改、完成任务；新建、修改笔记 |

只写日报就给 `space:me:read` + `space:report:write`；要让 AI 顺带补记任务、笔记，再加 `space:me:write`。建议设过期时间。

**3. 接入 Claude Code**（`-s user` 让所有项目都能用）：

```bash
claude mcp add -s user --transport http nebula-space \
  https://space.orccode.com/api/space/me/mcp \
  --header "apikey: AK-xxxx"
```

本地开发把地址换成 `http://localhost:19000/space/me/mcp`（直连网关）。`claude mcp list` 显示 connected 即可，之后在任何会话里说「帮我整理今天的工作，写日报」就行。

## 二、整体机制

```
Claude Code ──POST /api/space/me/mcp（请求头 apikey）──▶ nginx ──▶ 网关：只验 key 本身
     ──▶ space：API Key 守卫（端点标了 @SaCheckApiKey，key 至少有一个范围；写入 UserContext）
     ──▶ SpaceMcpController ──▶ SpaceMcpTransport（JSON-RPC 收发）──▶ SDK 无状态服务端
     ──▶ 工具：再校验这个工具要的范围 ──▶ space 的 service（按 UserContext 隔离数据）
```

| 类 | 作用 |
|---|---|
| `SpaceMcpController` | 端点。POST 收 JSON-RPC；GET（服务端推送流）按协议回 405 |
| `SpaceMcpTransport` | 实现 SDK 的 `McpStatelessServerTransport`：反序列化报文、交给 SDK、写回响应 |
| `SpaceMcpConfig` | 组装 `McpStatelessSyncServer`，开 `immediateExecution` |
| `SpaceMcpTools` | 15 个工具的定义与实现，下发给客户端的使用说明（`INSTRUCTIONS`） |
| `SpaceMcpScopeChecker` | 按工具校验授权范围 |
| `DayDigest` | `get_day` 的结果：一天的材料按日报段落预先分组 |

### 为什么不用 SDK 自带的 Servlet

`HttpServletStatelessServerTransport` 是独立注册的 Servlet，走不到 Spring MVC 的拦截器：API Key 守卫的默认拒绝、授权范围、把 key 的所属用户写入 `UserContext` 都不会发生。所以自己实现一个很薄的 transport，让请求经过普通的控制器方法。

### 为什么要 `immediateExecution`

SDK 默认把同步工具丢到 `boundedElastic` 线程池执行，而 `UserContext` 和 Sa-Token 的请求上下文都是线程绑定的，换了线程就取不到当前用户。开了之后工具在请求线程上同步执行。

### 授权范围为什么要校验两次

端点只有一个，守卫只能判断「key 至少具备其中一个范围」（`@SaCheckApiKey(mode = OR)`）。具体某个工具要哪个范围，调工具时由 `SpaceMcpScopeChecker` 再判一次，不够就以工具错误返回，模型能看到原因。

登录态请求（不带 key）也能调这个端点，此时与其它 `/me` 接口一样只看登录、不按工具设限。

### 为什么不开放 REST 接口

AI 走 MCP 就够了，工具直接调 service。开放面越小越好：多开放一个 REST 接口就多一个要维护授权范围的地方。以后真有脚本要直接调 REST，再按 API Key 设计文档第四节给具体接口标注解。

## 三、工具清单

| 工具 | 授权范围 | 说明 |
|---|---|---|
| `get_day` | 读 | 一天的材料：完成的任务、当天到期 / 逾期 / 下一天到期的、会议（含正文）、专注、当天写的笔记、已保存的日报 |
| `get_week_review` | 读 | 周回顾材料（即 `/me/review/weekly`） |
| `list_tasks` / `list_task_lists` | 读 | 任务、清单 |
| `list_notes` | 读 | 随手记，默认最多 50 条 |
| `list_meetings` | 读 | 日期范围内的会议，起止必填 |
| `list_reports` / `get_report` | 读 | 日报周报 |
| `search` | 读 | 全局搜索语法；只返回任务、清单、笔记、会议、日报周报，不含稍后读、人物、书签 |
| `save_report` | 写日报 | 整份保存；**已有内容时默认不覆盖**，返回已有内容让模型合并后带 `overwrite=true` 再存；正文不能为空 |
| `create_task` | 写 | 新建任务；`done=true` 直接标为完成，用来补记做完的工作 |
| `update_task` / `complete_task` | 写 | 局部修改、完成 / 撤销完成 |
| `create_note` / `update_note` | 写 | 新建、局部修改笔记 |

几条约定：

- **日期一律由客户端传**（`get_day`、`save_report` 等必填）。服务器在首尔、用户可能在别处，用服务器的「今天」会差一天。`list_tasks` 的 today / plan 视图仍以服务器日期为准，说明里已提示模型优先用 `get_day`。
- **ID 是字符串**，与页面接口一致（避免前端丢精度）。工具结果用与接口返回同一个 JsonMapper 序列化，协议报文则用 Jackson 默认配置，互不影响。
- **参数不允许多余字段**。SDK 按 inputSchema 校验，`due_date` 这种拼错会直接报错而不是被悄悄忽略；DTO 上的 Bean Validation 规则再校验一遍。
- **业务错误以 `isError` 结果返回**（如任务不存在、key 缺范围），模型能看到原因并自行修正；只有协议层错误才是 JSON-RPC error。
- **没有删除类工具**。`save_report` 拒绝空正文，因为 service 里空正文等于删除。

### 日报格式

初始化时下发的说明（`SpaceMcpTools.INSTRUCTIONS`）要求模型沿用页面「插入记录」的结构（前端 `views/review/reportDraft.ts`）：

```markdown
### 完成
1. 任务标题（专注 1 小时 20 分）

### 会议
- 会议名：决议

### 周四计划
1. ……

### 没做完
- 任务标题（逾期 2 天）
```

「计划」二字不能丢：周回顾从带「计划」的标题下读列表项，对照下一周做到没有。

## 四、依赖

`nebula-parent` 管理版本 `mcp-sdk.version=2.0.0`，space 引入 `mcp-core` + `mcp-json-jackson3`。后者带入 `json-schema-validator`（校验工具参数）及其依赖 `jackson-dataformat-yaml`，版本由 Spring Boot 管理（Jackson 3）。SDK 不带自动配置，聚合部署下其它服务的上下文不受影响。

## 五、验证情况

单元测试 21 个（`com.nebula.space.mcp`）：

- 走完整 SDK 链路：初始化、列工具、调工具、通知回 202、未知方法、报文损坏、参数不符合 schema。
- 工具在请求线程上执行，取得到 `UserContext` 里的当前用户。
- key 缺范围被拒且不调 service；日报已有内容时不覆盖、`overwrite=true` 才覆盖、空正文被拒；`create_task` 带 `done`；`update_task` 只带传了的字段；Bean Validation 与 BizException 转为工具错误；搜索不返回人物等模块。

本地起 space 独立进程（不经网关）用 curl 实测：上下文正常启动；初始化返回服务信息与说明，通知回 202，列出 15 个工具；未登录也无 key 时调工具返回「未获取到当前用户」；带无效 key 被守卫拒绝；GET 回 405。

**没有实测的**：带有效 key 经网关调用、在 Claude Code 里实际连通。

## 六、已知不足与后续

- **补记的任务完成时间是当前时间**，补记昨天的工作会算到今天。要支持需给 `complete` 加一个完成时间参数。
- **会议、习惯打卡、记账没有写工具**。会议正文结构较复杂（议程、待办同步），先只读。
- **`tools/list` 不按 key 的范围过滤**，只读 key 也能看到写工具，调用时才被拒。工具说明里写明了所需范围。
- **个人用户还不能自助签 key**：签 key 要 `system:api-key:add`，目前只该给可信角色。个人空间若要给普通用户用，需要一个「只能给自己签 `space:*` 范围」的入口。
