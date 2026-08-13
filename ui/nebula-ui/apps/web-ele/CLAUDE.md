# web-ele UI 规约（唯一标准，不得随意变更）

本文件是 `apps/web-ele` 的**唯一 UI 规则来源**。所有页面必须遵守，不允许按个人偏好另起一套写法。

**改动本规约的唯一流程**：先改本文件 → 再改代码。禁止「代码先偏离、事后补文档」。若某规则确实拦路，在 PR 里说明原因并同步修订本文件，不要默默绕过。

**黄金法则**：写新页面时不要凭空设计，直接复制最接近的既有页面再改。

- 简单列表（无分页、前端全量）→ 抄 [src/views/blog/tag/index.vue](src/views/blog/tag/index.vue)
- 分页 + 搜索列表 → 抄 [src/views/system/user/index.vue](src/views/system/user/index.vue)

---

## 0. 两个最容易踩的坑

1. 本仓库是 Vben Admin 的改名 fork，包名是 `@nebula/*`，**不存在 `@vben/*`**。Hook 名被机械改名为小写 n：**`usenebulaVxeGrid`**、`usenebulaForm`、`usenebulaDrawer`。写成 `useVbenVxeGrid` 一定报错（全仓 0 处）。
2. 虽然源自 Vben，但**业务页面不用 `usenebulaForm` / `usenebulaModal` / `usenebulaDrawer`**，一律直接用 Element Plus 的 `ElDialog` + `ElForm`。唯一的例外是框架示例 `views/demos/form/basic.vue`，不要参照它。

---

## 1. 页面结构

- 列表页外壳固定为 `<Page auto-content-height>`（来自 `@nebula/common-ui`）。
- 表格一律用 `usenebulaVxeGrid`（`#/adapter/vxe-table`），**39/125 个视图如此，`ElTable` 0 次用作页面主表**。
- `ElTable` 仅允许出现在弹窗/抽屉内的嵌套子表。

```ts
const [Grid, gridApi] = usenebulaVxeGrid({ gridOptions });
```

两种取数方式，按后端接口选其一，不要混：

- **A. Grid proxy 驱动（分页接口，首选）**：`proxyConfig.ajax.query` 调分页 API。若后端返回 `PageResult`，必须覆盖响应字段映射：
  ```ts
  proxyConfig: {
    response: { result: 'records', total: 'total' },
    ajax: { query: async ({ page }, formValues) => await getXxxPageApi({ ... }) },
  }
  ```
  （全局默认是 `items`/`total`，见 [src/adapter/vxe-table.ts](src/adapter/vxe-table.ts)，返回 `records` 时不覆盖会取不到数据。）
- **B. 手动加载（全量小数据）**：`proxyConfig: { enabled: false }` + `pagerConfig: { enabled: false }`，在 `onMounted` 里 `gridApi.setGridOptions({ data })`。

列配置 `gridOptions` **内联写在 `.vue` 里**，不要抽 `data.ts`（全仓 0 个 `data.ts`）。

## 2. 表单

职责分明，两者不可互换：

- **搜索/筛选表单** → 写在 grid hook 的 `formOptions.schema`（声明式 `component` / `fieldName` / `label`），配 `showSearchForm: true`。
- **新增/编辑表单** → 直接 `ElDialog` + `ElForm` + `FormInstance` / `FormRules`，`reactive` 建模型，手动 `validate()`。

```ts
const ok = await editFormRef.value.validate().catch(() => false);
if (!ok) return;
```

## 3. API 层

- 路径：`src/api/<domain>/<resource>.ts`；每个 domain 一个 `index.ts` 做 barrel；根 `src/api/index.ts` 汇总。
- **视图一律 `import { xxxApi } from '#/api'`，禁止深路径导入。**
- 类型**内联**在同文件顶部的 `export namespace <Domain><Resource>Api { ... }` 中。不建 `types.ts`（全仓 0 个），不在 namespace 外裸写 `export interface`。
- 函数命名：`<verb><Domain><Resource>Api`，**必须以 `Api` 结尾**（288 个导出中 278 个符合；`src/api/system/menu.ts` 的 7 个是历史遗留，不要模仿）。
- 常用动词：`get*PageApi` / `get*ListApi` / `get*DetailApi` / `create*Api` / `update*Api` / `delete*Api`。
- 返回类型标在 `requestClient` 调用上，不标在函数签名：

```ts
export async function getBlogTagListApi() {
  return requestClient.get<BlogTagApi.TagItem[]>('/blog/admin/tags');
}
```

## 4. 路由

- 位置：`src/router/routes/modules/<domain>.ts`，`export default routes: RouteRecordRaw[]`。
- 结构：一个无 component 的顶层分组路由 + `children` 懒加载 `() => import('#/views/...')`。
- `name`：**PascalCase 且带父级前缀**（`Blog` → `BlogTag` → `BlogSeriesCatalog`）。
- `path`：**kebab-case 绝对路径**，子路由重复父前缀（`/blog/import-task`）。
- `meta` 只用这些键：`title`(47) / `icon`(45) / `order`(12，仅分组路由) / `hideInMenu` / `activePath` / `affixTab`。
- 图标固定 Iconify `lucide:*` 字符串。
- **`authority`、`keepAlive` 全仓未使用**，不要新增；权限走按钮级 `v-access`（见 §10）。
- `title` 写**中文字面量**（45 处），不写 `$t()`（仅 dashboard 2 处为框架自带）。

## 5. 文案与 i18n

**业务页面一律硬编码中文，不要引入 `$t` / `useI18n`。**

125 个视图中 116 个含中文字面量，使用 i18n 的仅 4 个，全部是 `_core/authentication` 框架登录页。新页面加 `$t` 会与 100% 的现有业务页面不一致。

## 6. 样式

- 布局/间距用 **Tailwind 行内类**，这是默认手段。
- 确需自定义 CSS 时才加 `<style scoped>`；**必须带 `scoped`**（现存 8 个无 scoped 的是样式泄漏，属技术债，不要新增）。
- **禁止 `lang="scss"`**（全仓 0 处），只写原生 CSS。
- 颜色用设计变量 `hsl(var(--primary))` / `var(--border)` 等（409 处），**不要写死 hex**（224 处 hex 集中在 ai-flow 的 X6 画布节点，因 X6 必须字面色值，属受限例外）。

## 7. 组件写法

- **统一 `<script lang="ts" setup>`**，`lang` 在前（格式化器强制；6 处 `setup lang` 顺序是历史遗留）。
- 页面级组件必须写 `defineOptions({ name: 'PascalCase' })`，**与路由 `name` 保持一致**（110/125）。
- **Element Plus 必须显式 import，无组件自动导入。** `vite.config.ts` 只装了 `unplugin-element-plus`（仅自动引样式），仓库没有 `unplugin-vue-components` / `unplugin-auto-import`。用了 `El*` 标签却没 import 会直接渲染不出来。
- import 顺序由 perfectionist 强制：`import type` → `vue` → `@nebula/*` → `element-plus` → `#/adapter` → `#/api`。别手动调，交给 `pnpm format`。

## 8. 命名与目录

- 视图目录 kebab-case，主页面固定 `index.vue`：`views/<domain>/<resource>/index.vue`。
- 同目录次级页面用小写/kebab：`versions.vue`、`catalog.vue`、`detail.vue`。
- 可复用组件 PascalCase：`AgentVersionDrawer.vue`、`FlowToolbar.vue`。
- **默认一个功能目录就只有 `index.vue`**。只有页面确实复杂（如 ai-flow 编辑器）才开 `components/` 子目录（全仓仅 4 处）。

## 9. 提示与确认

- Toast 用 `ElMessage`（58 个文件；success/error/warning/info）。
- 危险操作确认用 `ElMessageBox.confirm`（37 个文件，47 次，**全部是 `.confirm`**，无 `.alert`/`.prompt`）。
- **`ElNotification` 不用于业务页面。**
- 删除确认固定写法（用 `try/catch` 吞掉取消，不用 `.catch()`）：

```ts
try {
  await ElMessageBox.confirm(`确认删除标签“${row.name}”吗？`, '提示', {
    type: 'warning',
  });
} catch {
  return;
}
await deleteBlogTagApi(row.id);
ElMessage.success('删除成功');
await reloadGrid();
```

## 10. 权限

- **唯一机制是按钮级指令 `v-access:code`**（191 处 / 40 个文件），由 `registerAccessDirective` 在 [src/bootstrap.ts](src/bootstrap.ts) 注册。
- 码格式：`<domain>:<resource>:<action>`，例 `blog:tag:add`、`manager:ai-agent:run`。
- 每个增删改操作按钮都要挂：`v-access:code="'blog:tag:delete'"`。
- `AccessControl` 组件与 `useAccess()` 存在但**视图中 0 次使用**，不要引入；路由 `authority` 同样不用。

---

## 11. 视觉规范锁定（主题/圆角/菜单，唯一来源）

全站视觉参数**只在 [src/preferences.ts](src/preferences.ts) 定义**，已锁定。禁止在页面里写局部覆盖来「改得好看点」。

**已关闭用户自定义**：`app.enablePreferences: false` 关掉右侧偏好设置抽屉，`widget.themeToggle: false` 关掉主题切换，`sidebar.draggable: false` 禁止拖拽改菜单宽度。这三项是「不让其随意更改」的落点，改动前请三思。

锁定的关键值：

| 项 | 值 | 说明 |
|---|---|---|
| `theme.mode` | `light` | 框架默认是 `dark`，此处显式锁亮色 |
| `theme.radius` | `'0.5'` | 圆角，字符串 rem；映射为 `--radius: 0.5rem` |
| `theme.colorPrimary` | `hsl(212 100% 45%)` | 主色 |
| `theme.fontSize` | `16` | 基准字号 |
| `navigation.styleType` | `rounded` | 菜单项圆角风格；另一选项 `plain` |
| `navigation.accordion` | `true` | 同级只展开一个 |
| `sidebar.width` / `collapseWidth` | `224` / `60` | 菜单展开/折叠宽度 |
| `tabbar.styleType` | `chrome` | 页签风格；可选 `card`/`plain`/`brisk` |
| `layout` | `sidebar-nav` | 全站唯一布局形态 |

### ⚠️ 改配置不生效？必须看这条

`initPreferences` 内部是 `merge({}, cachedPreferences, initialPreferences)`，而 `merge` 就是 **defu —— 前面的参数优先**（见 `packages/@core/base/shared/src/utils/merge.ts`）。

即：**用户 localStorage 里的旧配置会压过 `preferences.ts` 的新值**。所以

> **每次修改 `preferences.ts` 的锁定项，必须把 `.env` 里 `VITE_APP_NAMESPACE` 末尾版本号 +1**（如 `nebula-web-ele-v2` → `-v3`）。

命名空间是缓存 key 前缀，换掉即让所有旧客户端缓存整体失效，无需挨个通知用户清缓存。只改 `preferences.ts` 而不升版本号，**老用户看到的仍是旧样式**，这是最常见的「我改了但没生效」的原因。

## 12. 工程约束（工具已强制，别绕过）

- 包管理器只允许 **pnpm**（`preinstall: only-allow pnpm`），Node 版本见 `.node-version`（22.22.0）。
- 依赖版本统一走 `pnpm-workspace.yaml` 的 **catalog**，`package.json` 里写 `"catalog:"`，不要写死版本号。
- 提交前 lefthook 自动跑 `oxfmt` → `oxlint --fix` → `eslint --fix` → `stylelint --fix`；commit message 走 commitlint（conventional）。
- 提交前自检：`pnpm check`（循环依赖 / 依赖 / 类型 / 拼写）。
- 开发：`pnpm dev:ele`；构建：`pnpm build:ele`。

## 13. 已知技术债（勿模仿、勿扩散）

- `src/router/guard.ts.orig`、`src/router/routes/modules/ai-flow.ts.orig`、`src/views/ai-flow/index.vue.orig` 是合并遗留文件，应删除。
- `src/api/system/menu.ts` 的 7 个导出缺 `Api` 后缀。
- 8 处无 `scoped` 的 `<style>`（7 处在 ai-flow 编辑器，为穿透 Element Plus 内部样式）。
- 6 处 `<script setup lang="ts">` 属性顺序不一致。
