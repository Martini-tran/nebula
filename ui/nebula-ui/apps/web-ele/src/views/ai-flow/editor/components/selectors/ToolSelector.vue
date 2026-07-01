<script lang="ts" setup>
import type { AiToolApi } from '#/api';

import { onMounted, ref, watch } from 'vue';

import { ElOption, ElSelect } from 'element-plus';

import { getAiToolPageApi } from '#/api';

defineOptions({ name: 'ToolSelector' });

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '选择工具',
  size: 'default',
  clearable: true,
});

const emit = defineEmits<{
  change: [item: AiToolApi.ToolItem | undefined];
  'update:modelValue': [value: string];
}>();

interface Props {
  modelValue?: string;
  placeholder?: string;
  size?: 'default' | 'large' | 'small';
  clearable?: boolean;
}

const loading = ref(false);
const options = ref<AiToolApi.ToolItem[]>([]);

function labelOf(item: AiToolApi.ToolItem) {
  return `${item.name || item.toolCode}（${item.toolCode}）`;
}

async function search(keyword?: string) {
  loading.value = true;
  try {
    const res = await getAiToolPageApi({
      pageNum: 1,
      pageSize: 100,
      keyword: keyword || undefined,
      // 仅可用工具（enabled=1）供选择
      enabled: 1,
    });
    options.value = res.records ?? [];
  } finally {
    loading.value = false;
  }
}

/** 回显：当前值不在选项里时，用 keyword=code 兜底查一次并 pin 进选项 */
async function ensureOption(code: string) {
  if (!code) return;
  if (options.value.some((o) => o.toolCode === code)) return;
  const res = await getAiToolPageApi({
    pageNum: 1,
    pageSize: 100,
    keyword: code,
  });
  const hit = (res.records ?? []).find((o) => o.toolCode === code);
  if (hit) options.value = [hit, ...options.value];
}

function onChange(value: string) {
  emit('update:modelValue', value);
  emit(
    'change',
    options.value.find((o) => o.toolCode === value),
  );
}

watch(
  () => props.modelValue,
  (val) => {
    if (val) ensureOption(val);
  },
  { immediate: true },
);

onMounted(() => {
  search();
});
</script>

<template>
  <ElSelect
    :model-value="props.modelValue"
    :clearable="props.clearable"
    :loading="loading"
    :placeholder="props.placeholder"
    :remote-method="search"
    :size="props.size"
    filterable
    remote
    remote-show-suffix
    @change="onChange"
  >
    <ElOption
      v-for="item in options"
      :key="item.toolCode"
      :label="labelOf(item)"
      :value="item.toolCode"
    />
  </ElSelect>
</template>
