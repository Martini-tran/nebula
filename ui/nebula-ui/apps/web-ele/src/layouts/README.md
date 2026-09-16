# 双列导航

布局依据 `ui/variant-05-dual-sidebar.html`，入口为 `basic.vue`。

- `navigation.ts` 将 `accessStore.accessMenus` 分成工作台、AI、中转、内容、空间、插件和系统；只展示已授权业务域，未知模块保留为独立入口。
- `@nebula/layouts` 的 `DualSidebarLayout` 负责两列导航、面包屑、二级菜单配色与窄屏抽屉，复用原有页签、路由缓存、全局搜索和内容尺寸监听。
- `meta.activePath` 用于隐藏详情页的菜单定位；菜单参数、外链和各业务域最近访问页均保留。
- 流程编辑器继续使用 `full-content`，离开时由路由守卫恢复双列布局。恢复标记与应用环境命名空间隔离。
- `src/preferences.ts` 是尺寸、主色及外壳颜色变量的配置入口。二级菜单配色与折叠状态保存在原有偏好缓存中。
- `LAYOUT_PREFERENCES_VERSION` 加入实际缓存前缀，防止部署环境沿用旧 `.env` 时覆盖新布局。版本升级会使旧登录状态与页签缓存失效，需要重新登录。

业务页面继续使用现有 `Page` 和 VXE 表格，原型中的示例数据不会进入应用。

## 验证

从 `ui/nebula-ui` 执行：

```sh
pnpm exec vitest run apps/web-ele/src/layouts/navigation.test.ts packages/effects/layouts/src/basic/dual-sidebar/navigation.test.ts
pnpm --filter @nebula/web-ele typecheck
pnpm build:ele
pnpm dev:ele
```

菜单单测覆盖权限裁剪、不修改源菜单、禁用父级、未知模块、路径边界和参数保留。浏览器验证应覆盖跨模块返回、页签、菜单配色记忆、折叠后的表格尺寸、外链、编辑器全屏进出及移动端抽屉。
