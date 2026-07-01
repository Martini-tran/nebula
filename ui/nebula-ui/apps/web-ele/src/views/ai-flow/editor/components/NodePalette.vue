<script lang="ts" setup>
import type { AiFlowApi } from '#/api';

import { createIconifyIcon } from '@nebula/icons';

import { DEFAULT_NODE_TYPE } from '../constants';

defineOptions({ name: 'NodePalette' });

// 面板已收敛为单一「通用节点」入口；nodeTypes 仍由父级传入但当前不用于渲染分组
defineProps<Props>();

const emit = defineEmits<{
  add: [type: string];
}>();

interface Props {
  nodeTypes: AiFlowApi.NodeTypeMeta[];
}

const NodeIcon = createIconifyIcon('lucide:box');

function onDragStart(event: DragEvent) {
  event.dataTransfer?.setData('text/plain', DEFAULT_NODE_TYPE);
}
</script>

<template>
  <div class="w-48 shrink-0 border-r bg-white dark:bg-[#1d1e1f]">
    <div class="border-b px-4 py-3">
      <div class="text-sm font-medium">节点</div>
      <div class="mt-0.5 text-xs text-gray-400">拖拽或双击添加到画布</div>
    </div>

    <div class="p-3">
      <div
        class="flex cursor-move items-center gap-2 rounded-md border border-[#409eff] px-3 py-2.5 text-sm text-[#409eff] transition-shadow select-none hover:shadow-sm"
        draggable="true"
        @dragstart="onDragStart"
        @dblclick="emit('add', DEFAULT_NODE_TYPE)"
      >
        <NodeIcon class="text-base" />
        <span class="font-medium">通用节点</span>
      </div>
      <div class="mt-2 pl-1 text-[11px] leading-4 text-gray-400">
        拖入后在右侧属性面板选择「模型调用 / 工具调用」
      </div>
    </div>

    <div class="mx-3 border-t px-1 pt-3 text-[11px] leading-5 text-gray-400">
      · 左键框选 / 拖拽移动<br />
      · 右键拖动平移画布<br />
      · Ctrl/⌘+滚轮缩放<br />
      · Del 删除 · Ctrl+C/V 复制<br />
      · Ctrl+Z/Y 撤销重做<br />
      · 拖端点连线（连线可设分支条件）
    </div>
  </div>
</template>
