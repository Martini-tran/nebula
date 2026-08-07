// 隔离探针：复刻自研 dock 的 state + 模板核心（与 EditorSideDock 一致），面板内容用占位。
// 验证：活动栏两图标平铺 / 点击 toggle / 拖宽 / 关闭 / tab / 上下分屏 / 配色贴合 element-plus。
import { createApp } from 'vue';

import DockProbe from './DockProbe.vue';

import 'element-plus/theme-chalk/index.css';

const app = createApp(DockProbe);
app.mount('#app');
