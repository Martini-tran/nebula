# VXE Table Plugin

基于 vxe-table 和 vxe-pc-ui 的表格组件插件。

## 导出

| 导出                  | 类型 | 说明           |
| --------------------- | ---- | -------------- |
| `setupnebulaVxeTable`   | 函数 | 初始化配置函数 |
| `usenebulaVxeGrid`      | 函数 | 表格组合式函数 |
| `nebulaVxeGrid`         | 组件 | 表格组件       |
| `VxeTableGridColumns` | 类型 | 表格列类型     |
| `VxeTableGridOptions` | 类型 | 表格配置类型   |
| `VxeGridProps`        | 类型 | 表格 Props     |
| `VxeGridListeners`    | 类型 | 表格事件类型   |

## 使用

```ts
import {
  setupnebulaVxeTable,
  usenebulaVxeGrid,
  nebulaVxeGrid,
} from '@nebula/plugins/vxe-table';
```

## 初始化

在应用入口处调用：

```ts
import { setupnebulaVxeTable } from '@nebula/plugins/vxe-table';
import { usenebulaForm } from '@nebula-core/form-ui';

setupnebulaVxeTable({
  configVxeTable: (vxeUI) => {
    // 配置 VXE Table
  },
  usenebulaForm,
});
```

## 类型

```ts
import type {
  VxeTableGridOptions,
  VxeGridProps,
} from '@nebula/plugins/vxe-table';
```






