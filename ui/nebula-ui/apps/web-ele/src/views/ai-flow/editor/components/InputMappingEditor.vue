<script lang="ts" setup>
import type { UpstreamVar } from '../composables/useUpstreamVars';

import { computed } from 'vue';

import { ElButton, ElInput, ElOption, ElSelect } from 'element-plus';

defineOptions({ name: 'InputMappingEditor' });

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  keyPlaceholder: '变量名',
  valuePlaceholder: '上下文键',
  options: () => [],
});

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, string>];
}>();

interface Props {
  /** 映射：模板变量名/工具入参名 → 上下文键 */
  modelValue?: Record<string, string>;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
  /**
   * 可选：上游可用变量候选（来自 useUpstreamVars）。传入后「上下文键」侧
   * 变为可搜索下拉，选而非打；仍保留 allow-create 兜底手输任意键。
   * 不传则退化为纯文本输入（下拉无候选但仍可 create）。
   */
  options?: UpstreamVar[];
}

/** 内部用有序数组编辑，避免对象键顺序抖动 */
const rows = computed<Array<{ k: string; v: string }>>(() =>
  Object.entries(props.modelValue ?? {}).map(([k, v]) => ({ k, v })),
);

function emitRows(next: Array<{ k: string; v: string }>) {
  const obj: Record<string, string> = {};
  for (const { k, v } of next) {
    if (k.trim()) obj[k.trim()] = v;
  }
  emit('update:modelValue', obj);
}

function updateKey(index: number, key: string) {
  const next = rows.value.map((r) => ({ ...r }));
  next[index]!.k = key;
  emitRows(next);
}

function updateValue(index: number, value: string) {
  const next = rows.value.map((r) => ({ ...r }));
  next[index]!.v = value;
  emitRows(next);
}

function addRow() {
  emitRows([...rows.value, { k: '', v: '' }]);
}

function removeRow(index: number) {
  const next = rows.value.filter((_, i) => i !== index);
  emitRows(next);
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <div
      v-for="(row, index) in rows"
      :key="index"
      class="flex items-center gap-2"
    >
      <ElInput
        :model-value="row.k"
        :placeholder="keyPlaceholder"
        size="small"
        @update:model-value="updateKey(index, $event)"
      />
      <span class="text-gray-400">←</span>
      <!-- 上下文键侧：可搜索下拉（上游变量候选），allow-create 兜底手输 -->
      <ElSelect
        :model-value="row.v"
        :placeholder="valuePlaceholder"
        allow-create
        clearable
        default-first-option
        filterable
        size="small"
        style="flex: 1"
        @update:model-value="updateValue(index, $event)"
      >
        <ElOption
          v-for="opt in options"
          :key="opt.key"
          :label="opt.label"
          :value="opt.key"
        />
      </ElSelect>
      <ElButton link type="danger" size="small" @click="removeRow(index)">
        删除
      </ElButton>
    </div>
    <ElButton size="small" @click="addRow">+ 添加映射</ElButton>
  </div>
</template>
