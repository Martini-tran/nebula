<script lang="ts" setup>
import type { PaletteItem } from '../constants';

import type { AiFlowApi } from '#/api';

import { computed } from 'vue';

import { PALETTE_ITEMS } from '../constants';

defineOptions({ name: 'NodePalette' });

const props = defineProps<Props>();

const emit = defineEmits<{
  add: [type: string];
}>();

interface Props {
  nodeTypes: AiFlowApi.NodeTypeMeta[];
}

/** 后端实际支持的节点类型集合（node-types 接口返回） */
const supportedTypes = computed(
  () => new Set(props.nodeTypes.map((t) => t.type)),
);

/**
 * 面板项是否真正可拖：既标记为 draggable，且后端确有对应执行器。
 * 避免后端下线某执行器后前端仍能拖入一个跑不起来的节点。
 */
function isEnabled(item: PaletteItem): boolean {
  return item.draggable && !!item.nodeType && supportedTypes.value.has(item.nodeType);
}

function onDragStart(event: DragEvent, item: PaletteItem) {
  if (!isEnabled(item) || !item.nodeType) return;
  event.dataTransfer?.setData('text/plain', item.nodeType);
}

function onDblClick(item: PaletteItem) {
  if (!isEnabled(item) || !item.nodeType) return;
  emit('add', item.nodeType);
}
</script>

<template>
  <div class="w-48 shrink-0 border-r bg-white dark:bg-[#1d1e1f]">
    <div class="border-b px-4 py-3">
      <div class="text-sm font-medium">节点</div>
      <div class="mt-0.5 text-xs text-gray-400">拖拽或双击添加到画布</div>
    </div>

    <div class="space-y-2 p-3">
      <div
        v-for="item in PALETTE_ITEMS"
        :key="item.key"
        class="rounded-md border px-3 py-2 text-sm transition-colors select-none"
        :class="
          isEnabled(item)
            ? 'cursor-move hover:shadow-sm'
            : 'cursor-not-allowed opacity-55'
        "
        :style="
          isEnabled(item)
            ? { borderColor: item.color, color: item.color }
            : undefined
        "
        :draggable="isEnabled(item)"
        @dragstart="onDragStart($event, item)"
        @dblclick="onDblClick(item)"
      >
        <div class="flex items-center gap-2">
          <span
            class="inline-block h-2 w-2 shrink-0 rounded-full"
            :style="{ backgroundColor: item.color }"
          ></span>
          <span class="font-medium">{{ item.name }}</span>
        </div>
        <div v-if="item.hint" class="mt-1 pl-4 text-[11px] leading-4 text-gray-400">
          {{ item.hint }}
        </div>
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
