<script lang="ts" setup>
import type { AiFlowApi } from '#/api';

import { nodeMetaOf } from '../constants';

defineOptions({ name: 'NodePalette' });

defineProps<Props>();

const emit = defineEmits<{
  add: [type: string];
}>();

interface Props {
  nodeTypes: AiFlowApi.NodeTypeMeta[];
}

function onDragStart(event: DragEvent, type: string) {
  event.dataTransfer?.setData('text/plain', type);
}
</script>

<template>
  <div class="w-44 shrink-0 border-r bg-white p-3 dark:bg-[#1d1e1f]">
    <div class="mb-2 text-xs text-gray-400">拖拽或双击添加节点</div>
    <div
      v-for="nt in nodeTypes"
      :key="nt.type"
      class="mb-2 flex cursor-move items-center gap-2 rounded border px-3 py-2 text-sm select-none"
      :style="{
        borderColor: nodeMetaOf(nt.type).color,
        color: nodeMetaOf(nt.type).color,
      }"
      draggable="true"
      @dragstart="onDragStart($event, nt.type)"
      @dblclick="emit('add', nt.type)"
    >
      <span
        class="inline-block h-2 w-2 rounded-full"
        :style="{ backgroundColor: nodeMetaOf(nt.type).color }"
      ></span>
      {{ nt.name }}
    </div>

    <div class="mt-4 text-[11px] leading-5 text-gray-400">
      · 左键框选 / 拖拽移动<br />
      · 右键拖动平移画布<br />
      · Ctrl/⌘+滚轮缩放<br />
      · Del 删除 · Ctrl+C/V 复制<br />
      · Ctrl+Z/Y 撤销重做<br />
      · 拖端点连线（连线可设分支条件）
    </div>
  </div>
</template>
