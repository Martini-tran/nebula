# nebula API Key（SK）接入设计 v1

> 本文档记录 2026-10 落地的一批改动：**让脚本等无人值守的调用方凭一把 key 访问指定接口**，并用它开放了第一个接口——文章发布（可同时挂到系列）。
>
> **核心结论先说：**
>
> 1. key 的生成、存储、过期、禁用、授权范围校验全部用 Sa-Token 自带的 `sa-token-apikey` 插件，不自研。
> 2. **哪些接口能被 key 访问，由接口上的注解决定**；没标注解的接口，key 一律进不去。这条「默认拒绝」是框架不提供、我们自己补的唯一一块核心逻辑。
> 3. 网关没有数据库，校验 key 只读 Redis；库是真相源，由 manager 负责把库同步进 Redis。
>
> **本批次交付**：已实现并在本地实测。未实测的部分见[第九节](#九验证情况)。

---

## 一、解决什么问题

此前所有后台接口只认登录态，而登录要过验证码，脚本拿不到凭据。想从外部发布文章只能人工登录后台。

本批改动之后：在后台「系统管理 → API 密钥」签发一把 key，指定它能访问的范围，调用方把 key 放在请求头 `apikey` 里即可调用被开放的接口。

## 二、整体机制

```
                    ┌──────────────── Redis（Sa-Token 的存储格式）───────────────┐
                    │                                                            │
      写：增删改时同步 + 启动时 + 每 10 分钟                   读              读
                    │                                          │               │
   manager ─────────┘                                          │               │
   sys_api_key 表（真相源，key 为 AES 密文）                    │               │
                                                               │               │
   调用方 ──请求头 apikey──▶ 网关：只验 key 本身 ──原样透传──▶ 各服务：守卫 + 注解 ──▶ 接口
                            （有效 / 未过期 / 未禁用）          （默认拒绝 + 授权范围）
```

一次带 key 的请求要过三关：

| 关卡 | 位置 | 判什么 | 不通过 |
|---|---|---|---|
| 网关 | `AuthGlobalFilter` | key 本身是否有效、未过期、未禁用。通过后**不再要求登录** | HTTP 401 |
| 守卫 | `ApiKeyAccessInterceptor`（安全 starter，所有 servlet 服务自动装配） | 目标接口有没有标 `@SaCheckApiKey`，key 是否具备注解要求的授权范围 | 响应体 `code=403`（key 本身无效为 `401`） |
| 注解 | Sa-Token 的 `SaInterceptor` | `@SaCheckOr` 的「登录有权限 **或** key 有范围」 | 响应体带对应错误码 |

通过后，守卫把 key 的**所属用户**写入 `UserContext`，业务代码取当前用户的方式与登录态完全一致（例如文章的作者就是 key 的所属用户）。

### 为什么需要「守卫」这一层

`@SaCheckApiKey` 只负责「放行标注了的接口」，不负责「拦住没标注的接口」。而网关对带合法 key 的请求不再做登录校验，各服务里又有不少接口没有任何鉴权注解、只靠网关的登录校验兜底（例如 manager 的 `/role/list`）。不补这一层，任意一把合法 key 都能直接打到这些接口上。

守卫放在 `nebula-starter-security` 里，是为了让每个 servlet 服务自动带上——只要有一个服务漏装，带 key 的请求在那里就不受约束。

### 为什么不用 `StpUtil.switchTo` 给 key 赋身份

它会让请求以该用户的身份通过**所有** `@SaCheckPermission`，「只有标注的接口才能访问」随之失效。所以只写 `UserContext`，不动 Sa-Token 的登录态。

## 三、框架负责什么，自己写了什么

| 能力 | 谁负责 |
|---|---|
| 生成 key、保证不重复 | 框架 `SaApiKeyUtil.createApiKeyModel` |
| key 在 Redis 里的存储格式、按过期时间设 TTL | 框架 `SaApiKeyUtil.saveApiKey` |
| 校验有效 / 过期 / 禁用 / 授权范围 | 框架 `SaApiKeyUtil.checkApiKey` / `checkApiKeyScope` |
| 接口注解、「登录或 key」二选一 | 框架 `@SaCheckApiKey` + `@SaCheckOr(append = …)` |
| 从请求里读 key（参数 `apikey` → 请求头 `apikey` → Basic 认证） | 框架 `SaApiKeyUtil.readApiKeyValue` |
| key 落库、后台管理页面 | 自己写：`sys_api_key` 表、`SysApiKeyServiceImpl`、`views/system/api-key` |
| 库 → Redis 的同步 | 自己写：增删改时同步写 + `ApiKeyCacheSyncer` 定时重灌 |
| 网关放行带 key 的请求 | 自己写：`AuthGlobalFilter` 里一个分支 |
| 默认拒绝、把所属用户写入 `UserContext` | 自己写：`ApiKeyAccessInterceptor` |
| 鉴权异常转成统一响应（且不泄露完整 key） | 自己写：`ApiKeyExceptionHandler` |

## 四、怎么把一个接口开放给 key

两个注解成对出现：

```java
@PostMapping("/publish")
@SaCheckOr(permission = @SaCheckPermission("blog:article:publish"), append = SaCheckApiKey.class)
@SaCheckApiKey(scope = "blog:article:publish")
public R<PostPublishVO> publish(@RequestBody @Valid PostPublishRequest req) {
```

- `@SaCheckOr` 表达「登录且有权限」与「key 有授权范围」二选一，后台页面和脚本可以共用同一个接口。
- `@SaCheckApiKey` 既是 `@SaCheckOr` 的其中一支，也是守卫判断「该接口是否开放」的唯一依据。
- 只想让 key 访问、不接受登录态的接口（如服务间回调），单独标 `@SaCheckApiKey` 即可。

另有三个前提：

1. **授权范围的取值就是权限码**，要在 `sys_menu` 里有对应的按钮行，角色才能被授予、后台才能签进 key。
2. **该服务要注册了 `SaInterceptor`**，否则 `@SaCheckOr` 不生效。目前 manager、blog 已注册；space、forge、scribe 没有。只想让 key 访问的接口（单独标 `@SaCheckApiKey`）不受此限，守卫本身就会把关，space 的 MCP 端点就是这样，见[个人空间 MCP 接入设计](个人空间%20MCP%20接入设计.md)。
3. **key 管理接口自身不要开放**：key 不能用来给自己签发或修改 key。

## 五、文章发布接口

`POST /blog/admin/articles/publish`，权限码与授权范围均为 `blog:article:publish`。

```bash
curl -X POST https://<域名>/api/blog/admin/articles/publish \
  -H "apikey: AK-xxxx" -H "Content-Type: application/json" \
  -d '{
        "title": "Redis 集群（二）",
        "slug": "redis-cluster-02",
        "content": "# Markdown 正文",
        "series": { "slug": "redis-in-depth", "catalog": "集群篇", "sortOrder": 2 }
      }'
```

语义：

- **以 `slug` 为幂等键**（必填）：不存在则新建，已存在则更新，可放心重跑。更新时字段为空表示不改。
- **`status` 缺省为 `published`**，要存草稿需显式传 `draft`。
- 其余字段（`summary`、`postType`、`visibility`、`sourceType`、`categoryIds`、`tagIds` 等）与后台创建接口一致。
- **`series` 可选**，不传则不处理系列关联：
  - 用 `id` 或 `slug` 定位系列。**系列不存在直接报错，不自动创建**，避免拼错时悄悄多出一个系列；此时也不会留下文章。
  - 文章不直接属于系列，而是挂在系列下的「文章集合」目录节点上。`catalogId` 指定节点；`catalog` 按标题找，找不到则在系列根下新建；都不传就用系列下第一个「文章集合」节点，没有则新建「正文」。
  - `sortOrder` 不传则排到该节点末尾。重复挂载是幂等的。
- 返回 `id`、`slug`、`created`（本次是新建还是更新）、`seriesId`、`catalogId`。

挂载逻辑在 `BlogSeriesPostAttacher`，发布接口与迭代链回调共用。它是**追加**语义，不走 `bindPosts`（那是整节点全量替换）。

## 六、数据与配置

**表** `sys_api_key`（manager）：所属用户、名称、key 密文、key 掩码、授权范围（逗号分隔）、过期时间（空 = 永不过期）、状态、备注。

- key 存 **AES 密文**而不是哈希：Redis 丢数据后要靠它把 key 重新灌回去，必须可逆。
- 明文只在创建成功的那一次响应里返回，之后任何接口都只给掩码。

**权限码**（`sys_menu`）：

| 权限码 | 用途 |
|---|---|
| `system:api-key:list` / `add` / `edit` / `delete` | 后台管理 key |
| `blog:article:publish` | 发布接口；也是签发 key 时可选的授权范围 |

签发或修改 key 时，**操作人必须自己拥有所选的每一个权限**。否则有了「管理 key」的权限，就能给自己签一把什么都能干的 key。

**配置**：

| 配置项 | 说明 |
|---|---|
| `nebula.security.api-key.secret` | key 密文的加密密钥。缺省沿用 `AI_PROFILE_SECRET`，部署上不必多一个变量；可用 `API_KEY_SECRET` 单独指定。**上线产生数据后不可更换** |
| `nebula.security.api-key.resync-interval-ms` | 库 → Redis 的重灌间隔，默认 10 分钟 |

key 的前缀是框架默认的 `AK-`。没有配置 `sa-token.api-key.*`：聚合部署下该配置是 JVM 级静态单例，各服务取值不一致会互相覆盖。

**Redis 键**：`Authorization:apikey:<key 明文>`，另有按用户的索引 `Authorization:raw-session:apikey:<用户ID>`。明文作键名是框架行为。

## 七、上线步骤

1. 执行 `script/mysql/sys_api_key.sql`（建表、菜单、两处数据修正，可重复执行；重跑会清掉新菜单已有的角色授权）。
2. 重新部署**网关**和**后端**（聚合进程或各服务）。只发其中一个不行：网关不认 key 则进不来，后端没守卫则不设防。
3. 脚本没有给任何角色授权新菜单。超级管理员靠通配符天然拥有；其余角色按需在「角色管理」里勾选。`system:api-key:*` 只应给可信的角色。
4. 登录后台 → 系统管理 → API 密钥 → 新增，保存好弹窗里的明文。

## 八、顺带的改动

- **blog 的权限注解开始真正生效**。此前 blog 没有注册 `SaInterceptor`，控制器上的 `@SaCheckPermission` 不起作用，只有网关的登录校验。现在非超级管理员访问 blog 后台接口需要有对应权限码。
- **补 `blog:tag:query`**：标签详情接口用的这个权限码在 `sys_menu` 里一直没有对应行，注解生效后非超管角色将永远拿不到。脚本补了按钮 2204，并授给所有已有「标签管理」菜单的角色。
- **修迭代链回调的节点类型**：`series-append` 早先给系列建的「正文」节点是目录类型（`node_type=0`），而前台目录树只展示「文章集合」类型（`node_type=1`）节点下的文章。代码改为共用 `BlogSeriesPostAttacher`，脚本里一条 UPDATE 把历史节点原地改正（只动迭代链产出的系列里没有子节点的「正文」节点）。
- **修一个一直失败的单测**：`UserContextFilterTest` 里未登录用例的桩直接抛异常，而被覆盖方法的约定是吞掉异常返回空。改的是桩。

## 九、验证情况

本地先后用三个独立进程、以及聚合进程（5 个服务合一）加网关实测通过：

- 合法 key 发布并挂系列；同一请求重发走更新；连发两篇时第二篇排在后面。
- 合法 key 访问未标注的接口（blog、manager、space、scribe、forge）全部被拒，包括绕过网关直连服务。
- 伪造的、已禁用的 key 在网关被拒；授权范围不对的 key 被拒并提示缺哪个范围。
- 系列不存在时报错且不留下文章。
- 迭代链回调两轮加一次重放：节点类型正确、按轮次排序、重放幂等。
- 服务日志里没有出现完整的 key。

**没有实测的**（登录要过验证码，无法脚本化）：

- manager 的 key 创建、修改、启停、删除接口，以及后台管理页面。
- 登录态下 blog 各页面在权限注解生效后的表现。

## 十、已知不足与后续

- **授权范围靠手输**。后台下拉里只预置了 `blog:article:publish` 与个人空间 MCP 的三个范围。后续可在启动时扫描所有标了 `@SaCheckApiKey` 的接口同步成清单，供签发时勾选。
- **`series-append` 回调接口仍无鉴权**。可以给它标 `@SaCheckApiKey`，manager 回调时带上 key；需要先定 manager 从哪里取这把 key，以及只对哪些回调地址发送（不能把 key 发给任意配置的 URL）。
- **不记录 key 的最后使用时间**。
- **重灌只增不减**。绕过管理接口直接写库的 key 不会立即生效，要等下一次重灌（最长一个周期）或重启 manager。反过来，直接从库里删掉的 key 在 Redis 里不会被清掉：设了过期时间的等到过期才失效，永不过期的会一直有效。吊销 key 必须走管理接口。
- **key 放在查询参数里也能用，但会进入 nginx 访问日志**，应始终放请求头。
