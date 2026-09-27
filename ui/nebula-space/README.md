# nebula-space

个人空间前台：管理自己的书签、目录与标签，支持导入/导出 Chrome 书签 HTML。

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

多数模块还没有后端，接口统一走 `src/api/mock.ts` 的开关：

- `VITE_USE_MOCK=true`：未接通的模块返回假数据
- `VITE_REAL_MODULES=bookmarks`：已接通后端的模块（逗号分隔），即使开着 mock 也走真实接口

配置在 `.env.development` / `.env.production`。书签模块也有一份内存 mock（`src/api/space.mock.ts`），
没有后端时可以这样启动：`VITE_REAL_MODULES=none npm run dev`。

随手记、任务、会议的 mock 数据存在浏览器 localStorage（`nebula-space:mock:*`），刷新不丢；
清掉这些 key 就回到种子数据。接口路径按设计拟定为 `/space/me/**`，规则写在 `src/api/notes.ts`、`src/api/tasks.ts`、`src/api/meetings.ts` 顶部注释里，后端实现时照搬。

## 目录

```
src/
├── api/            # auth（登录/验证码）、space（书签/目录/标签/导入导出）、mock 开关
├── components/     # base（弹窗/确认/提示）、页头、品牌标识、主题切换、滑块验证码、状态块
├── composables/    # useToast、useConfirm
├── config/         # 模块注册表
├── layouts/        # AppLayout（带模块导航）、BlankLayout（登录 / 无权限）
├── router/         # 路由与登录守卫
├── stores/         # auth、theme、space（目录树与标签缓存）
├── styles/         # 设计令牌、主题、基础样式、共享原子类
├── types/
├── utils/          # request（axios 封装）、theme、format、date、markdown、taskParser（中文自然语言）、repeat
└── views/
    ├── bookmarks/  # 书签工作台：侧栏、网格/列表、批量操作、详情抽屉、导入导出
    │   └── organize/ # 整理页：目录拖拽、标签配色与合并、导入导出记录
    ├── today/      # 今天（登录后首页）：今日任务、会议时间线、随手记、今天收藏、晚间回顾
    ├── meetings/   # 会议：列表与模板、会前准备、会中记录（决议/待办识别）、纪要
    ├── notes/      # 随手记：便签墙、到期整理、编辑页（Markdown、/ 插入块、选中转任务）
    ├── tasks/      # 任务：收件箱/今天/计划看板/已完成、清单、自然语言快速添加、详情面板
    ├── login/
    ├── error/      # 无权限（403）
    └── placeholder/ # 规划中模块的占位页
```

## 说明

- 任何页面按 `Ctrl+Shift+Space`（或页头的「+」）呼出快速记录：笔记 / 任务 / 书签 / 会议，Tab 切换。

- 空间服务目前只提供 `/space/admin/**` 端点，数据按登录用户隔离，前台直接调用；登录账号需要具备 `space:*` 相关权限。
- 列表默认只展示「正常」状态的书签，「已归档」「失效链接」各自单独一个视图。
- 新建书签时后端遇到重复网址不会报错，而是用本次的标签覆盖已有书签的标签；前端在快速收藏与添加弹窗里先按规范化网址查重（`findDuplicate`）。
- 后端没有的组合操作由前端用现有接口拼出（非事务，中途失败会如实提示）：删除非空目录的三种处置、批量打标签 / 归档、标签合并。
