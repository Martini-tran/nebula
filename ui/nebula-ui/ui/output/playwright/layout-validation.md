# 双列布局验证记录

日期：2026-09-15

分支：`feature/nebula-dual-sidebar-layout`

- `pnpm build:ele`：通过，11 个构建任务成功。
- 菜单分组与导航目标单测：10 项通过。
- 改动文件的 ESLint、Oxlint、Stylelint、`git diff --check`：通过。
- 浏览器交互检查：13 项通过，包括二级配色及持久化、折叠和键盘可访问性、内容尺寸监听、菜单参数、业务域最近访问页、页签、外链、流程编辑器全屏进出、移动端溢出检查、菜单选择收起与 Escape 关闭。
- `pnpm --filter @nebula/web-ele typecheck`：5 处错误，与改造前基线完全一致。分别位于 `src/store/auth.ts`（1 处）、`src/views/ai-relay/provider/index.vue`（1 处）、`src/views/system/menu/index.vue`（3 处）。

浏览器运行真实 Vue 应用，接口由隔离的 Playwright 会话模拟；未进行后端联调。原型中的示例业务数据未接入应用代码。

预览：

- [桌面浅色菜单](layout-implemented-desktop.png)
- [桌面深色菜单](layout-implemented-dark.png)
- [手机二级菜单抽屉](layout-implemented-mobile.png)
- [流程编辑器全屏](layout-editor-fullscreen.png)
