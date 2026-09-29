# nebula-space

个人空间前台：书签、随手记、任务、会议、日历、习惯、专注、日报周报、稍后读、文件柜、记账、目标与纪念日、人物卡、公开主页，外加今天页、全局搜索与数据导出。

对接后端 `nebula-service-space`（网关路由 `/space/**`），登录复用 `nebula-service-manager` 的账号体系与滑块验证码。

## 技术栈

Vue 3 + TypeScript + Vite + Pinia + Vue Router + Tailwind CSS 4 + Iconify

## 开发

```bash
npm install
npm run dev      # http://localhost:28261
npm run build
```

开发服务器把 `/api/**` 代理到网关（默认 `http://localhost:19000`），并去掉 `/api` 前缀。
可在 `.env.local` 里用 `VITE_PROXY_TARGET` 覆盖网关地址。

## 模块与 mock

模块清单在 `src/config/modules.ts`（顶部导航、「更多」菜单、占位页都从这里读）。设计原型见 `docs/ui设计/个人空间/`。

所有模块都已接通后端（nebula-service-space），每个模块仍保留一份 mock，接口统一走 `src/api/mock.ts` 的开关：

- `VITE_USE_MOCK=true`：不在下一行名单里的模块返回假数据
- `VITE_REAL_MODULES=bookmarks,notes`：走真实接口的模块（逗号分隔），即使开着 mock 也不用假数据。现在名单里是全部模块：
  书签、随手记、任务、习惯、会议、专注、日报周报（含周回顾）、偏好设置、目标与纪念日、记账、稍后读、人物卡、文件柜、公开主页（`profile`）、
  全局搜索（`search`）、今天页（`today`）、日历（`calendar`）、导出全部数据（`export`）、法定节假日（`holidays`）

配置在 `.env.development` / `.env.production`。书签模块也有一份内存 mock（`src/api/space.mock.ts`），
没有后端时可以这样启动：`VITE_REAL_MODULES=none npm run dev`。

演示模式下多数模块的 mock 数据存在浏览器 localStorage（`nebula-space:mock:*`；文件柜的文件内容在 IndexedDB `nebula-space-files`），刷新不丢；
清掉这些 key（或在「设置 → 数据」里点「重置演示数据」）就回到种子数据。

文件柜的文件内容走公共文件组件（sys_file + MinIO 私有桶），单个文件上限 100 MB、每人 10 GB（后端 `nebula.file.max-file-size`、`nebula.space.files.quota`）。

不需要登录的两个页面：文件分享下载页 `/s/{code}`（接口 `/space/public/**`，网关白名单已放行）、公开主页 `/@{handle}`。mock 模式下它们的数据在分享人自己的浏览器里，只有同一个浏览器能打开。

全局搜索：`GET /space/me/search?q=` 把输入原样传给服务端，服务端解析同一套语法（`src/utils/searchQuery.ts` ↔ `SearchCriteria.java`），
在库里按「只会多不会少」的条件召回各模块候选（稍后读能搜到存档正文）；打分、排序、显示文字仍在前端 `src/api/search.ts` 里做，
因为「最近打开」只有浏览器知道，会议待办的负责人与截止日也沿用前端同一套解析。演示模式下各模块全部数据都当候选。

「今天」页、日历、周回顾各一次请求取齐几个模块的数据（`src/api/overview.ts`：`/space/me/today`、`/space/me/calendar`、`/space/me/review/weekly`），
服务端只把范围收窄到用得上的那几天，分组统计仍在页面里算。日历按国务院公告标「休 / 班」，数据是 space 服务里按年放的 JSON
（`src/main/resources/space/holidays/{year}.json`），新一年公告出来后加一个文件即可。

「设置 → 数据 → 导出全部数据」由服务端 `GET /space/me/export` 一次取齐各模块打成一份 JSON（任何一块取不到就整体失败）。

## 目录

```
src/
├── api/            # 每个模块一个文件（真实接口 + mock）；auth（登录/验证码）、space（书签/目录/标签/导入导出）、overview（今天/日历/周回顾聚合）、search、holidays、mock 开关
├── components/     # base（弹窗/确认/提示）、页头、品牌标识、主题切换、滑块验证码、状态块
├── composables/    # useToast、useConfirm、快速记录与全局搜索的开关、页面内提醒（useReminders）
├── config/         # 模块注册表（modules.ts）、各模块可选图标（icons.ts：Iconify lucide，不用表情）
├── layouts/        # AppLayout（带模块导航）、BlankLayout（登录 / 无权限）
├── router/         # 路由与登录守卫
├── stores/         # auth、theme、settings（偏好，改动即存）、space（目录树与标签缓存）、tasks、focus、badges、holidays（法定假日按年缓存）
├── styles/         # 设计令牌、主题、基础样式、共享原子类
├── types/
├── utils/          # request（axios 封装）、theme、format、date（含一周起始日）、markdown、taskParser（中文自然语言）、repeat、searchQuery（搜索语法）、recent（最近打开）、ledgerParser（记账一行输入）、lunar（农历换算，用浏览器 Intl）、files
└── views/
    ├── bookmarks/  # 书签工作台：侧栏、网格/列表、批量操作、详情抽屉、导入导出
    │   └── organize/ # 整理页：目录拖拽、标签配色与合并、导入导出记录
    ├── today/      # 今天（登录后首页）：今日任务、会议时间线、随手记、今天收藏、晚间回顾
    ├── calendar/   # 日历：月视图（会议/任务/习惯/心情、拖动改期）、周视图（七天时间轴，任务跨天拖动排时间）、日视图（把任务排进时间块）
    ├── habits/     # 习惯：本周打卡、连续与完成率、一年热力图、补打卡
    ├── focus/      # 专注统计（专注计时本身在 components/focus/FocusHost.vue，从任务发起）
    ├── review/     # 周回顾（五个数与上周对比、完成/决议/没做完/七天状态）与日报周报（正文是 Markdown、不分模板；可插入当天或本周的记录，周报可按日报汇总；复制 Markdown）
    ├── settings/   # 设置：模块开关、首页、主题、一周起始日、晚间与周回顾、随手记默认值、专注、稍后读、通知、导出
    ├── reading/    # 稍后读：阅读队列、阅读模式（划线 / 批注 / 转随手记与任务、记住位置）、摘录库
    ├── files/      # 文件柜：文件夹、上传与预览、最近删除、分享链接；shared.vue 是不需登录的下载页
    ├── ledger/     # 记账：一行记一笔、按天流水、分类环图、周期账单、分类预算与近 6 个月趋势
    ├── goals/      # 年度目标（进度订阅习惯 / 稍后读 / 记账 / 任务清单）与纪念日（农历、提前生成任务）
    ├── people/     # 人物卡：往来时间线、互相的承诺、该联系了、约 1:1
    ├── share/      # 公开主页设置与访客页 /@handle
    ├── meetings/   # 会议：列表与模板、会前准备、会中记录（决议/待办识别）、纪要
    ├── notes/      # 随手记：便签墙、到期整理、编辑页（Markdown、/ 插入块、选中转任务）
    ├── tasks/      # 任务：收件箱/今天/计划看板/已完成、清单、自然语言快速添加、详情面板
    ├── login/
    ├── error/      # 无权限（403）
    └── placeholder/ # 模块占位页（所有模块都已实现，留给以后新增的模块）
```

## 说明

- 任何页面按 `Ctrl+Shift+Space`（或页头的「+」）呼出快速记录：笔记 / 任务 / 书签 / 会议，Tab 切换。
- 任何页面按 `Ctrl+K` 呼出全局搜索；输入 `>` 切成命令模式。不在输入框里时，`G` 再按一个键跳转：T 今天、B 书签、N 随手记、D 任务、M 会议、C 日历、H 习惯、F 专注统计、R 日报、W 周回顾、S 设置。
- 任务提醒、习惯提醒只在页面开着时生效（浏览器通知，未授权时是页内提示）。

- 书签、目录、标签与导入导出走 `/space/admin/**`（需要 `space:*` 相关权限），其余模块走 `/space/me/**`（只要登录），数据都按登录用户隔离。
- 列表默认只展示「正常」状态的书签，「已归档」「失效链接」各自单独一个视图。
- 「失效链接」视图里点「检查链接」由服务器逐个访问网址（前端每批 16 条、两批并行）：域名不存在、连接被拒绝、404 / 410、Cloudflare 报源站故障（521/522/523/525/526/530）、证书过期或与域名不符直接标为失效；服务器超时这类查不清的，再用浏览器 no-cors 请求探一次（`views/bookmarks/browserProbe.ts`），连得上的不动，两边都连不上的列出来由用户勾选后标为失效（浏览器探测对 Cloudflare 人机验证等站点会误报，所以默认不勾）；可一键删除全部失效。
- 整理页的「AI 整理」：书签归类（归目录 / 打标签 / 改标题 / 补描述）与重排目录。AI 只出建议，勾选后前端用现有接口应用，标签只加不删；需要 space 服务配置 `nebula.ai`（环境变量 `AI_API_KEY` 等，与 blog 服务共用），每人每小时限 120 次调用。
- 新建书签时后端遇到重复网址不会报错，而是用本次的标签覆盖已有书签的标签；前端在快速收藏与添加弹窗里先查重（`GET /bookmarks/duplicate`，按规范化网址的哈希）。
- 快速收藏先用域名当标题，随后服务端抓网页（`POST /bookmarks/{id}/meta`，与稍后读同一个防 SSRF 的抓取器）补上网页标题与描述，只替换自动填的标题与空描述。
- 点开书签会记一次访问（`POST /bookmarks/{id}/visit`，不改更新时间），详情抽屉里的「最近访问 · 共 N 次」由此而来。
- 删除非空目录的三种处置（`DELETE /bookmark-folders/{id}?strategy=moveUp|uncategorize|cascade`）与标签合并（`POST /space-tags/{id}/merge?into=`）在服务端一个事务里做完，失败时什么都不改。
- 导入书签：上传后服务端建好任务马上返回，后台逐条入库，前端轮询任务显示进度；没导进来的书签逐条记下原因（最多 100 条），在导入结果与「导入记录」里能看到。上传完可以关掉窗口，导入照常继续。
- 导出书签：Chrome 兼容 HTML，或带目录路径、标签、描述、备注的 JSON（完整备份）；可勾选连同已归档的一起导出，失效的不导出。
- 仍由前端用现有接口拼出（非事务，中途失败会如实提示）的组合操作：批量打标签 / 归档、应用 AI 整理建议与目录重排方案。
