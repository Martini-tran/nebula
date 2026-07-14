<script lang="ts" setup>
import type { AiToolApi } from '#/api';

import { computed, onMounted, ref, watch } from 'vue';

import { ElOption, ElSelect } from 'element-plus';

import { getAiToolPageApi } from '#/api';

defineOptions({ name: 'ToolSelector' });

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '选择工具',
  size: 'default',
  clearable: true,
  multiple: false,
});

const emit = defineEmits<{
  change: [item: AiToolApi.ToolItem | AiToolApi.ToolItem[] | undefined];
  'update:modelValue': [value: string | string[]];
}>();

interface Props {
  /** 单选时为工具编码；multiple 时为工具编码数组 */
  modelValue?: string | string[];
  placeholder?: string;
  size?: 'default' | 'large' | 'small';
  clearable?: boolean;
  /** 多选模式：AGENT_REACT 节点的工具白名单用（模型可自选其中任意工具） */
  multiple?: boolean;
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

/** 当前值归一化为编码数组（单选/多选统一处理回显） */
const codes = computed<string[]>(() => {
  const v = props.modelValue;
  if (Array.isArray(v)) return v.filter(Boolean);
  return v ? [v] : [];
});

/** 回显：值不在选项里时，用 keyword=code 兜底查一次并 pin 进选项 */
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

/** 按编码取工具项（多选时逐个查，查不到的丢弃） */
function itemsOf(list: string[]): AiToolApi.ToolItem[] {
  const found: AiToolApi.ToolItem[] = [];
  list.forEach((code) => {
    const hit = options.value.find((o) => o.toolCode === code);
    if (hit) found.push(hit);
  });
  return found;
}

function onChange(value: string | string[]) {
  emit('update:modelValue', value);
  emit(
    'change',
    Array.isArray(value)
      ? itemsOf(value)
      : options.value.find((o) => o.toolCode === value),
  );
}

watch(
  codes,
  (list) => {
    list.forEach((code) => ensureOption(code));
  },
  { immediate: true, deep: true },
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
    :multiple="props.multiple"
    :placeholder="props.placeholder"
    :remote-method="search"
    :size="props.size"
    collapse-tags
    collapse-tags-tooltip
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
