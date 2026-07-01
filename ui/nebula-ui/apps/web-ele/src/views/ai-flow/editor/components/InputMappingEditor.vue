<script lang="ts" setup>
import { computed } from 'vue';

import { ElButton, ElInput } from 'element-plus';

defineOptions({ name: 'InputMappingEditor' });

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  keyPlaceholder: '变量名',
  valuePlaceholder: '上下文键',
});

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, string>];
}>();

interface Props {
  /** 映射：模板变量名/工具入参名 → 上下文键 */
  modelValue?: Record<string, string>;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
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
      <ElInput
        :model-value="row.v"
        :placeholder="valuePlaceholder"
        size="small"
        @update:model-value="updateValue(index, $event)"
      />
      <ElButton link type="danger" size="small" @click="removeRow(index)">
        删除
      </ElButton>
    </div>
    <ElButton size="small" @click="addRow">+ 添加映射</ElButton>
  </div>
</template>
