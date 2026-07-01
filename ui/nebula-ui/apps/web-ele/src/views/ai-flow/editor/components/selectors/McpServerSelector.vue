<script lang="ts" setup>
import type { AiMcpServerApi } from '#/api';

import { onMounted, ref, watch } from 'vue';

import { ElOption, ElSelect } from 'element-plus';

import { getAiMcpServerPageApi } from '#/api';

defineOptions({ name: 'McpServerSelector' });

const props = withDefaults(defineProps<Props>(), {
  placeholder: '选择 MCP 服务（可多选）',
  size: 'default',
  clearable: true,
});

const emit = defineEmits<{
  'update:modelValue': [value: string[]];
}>();

interface Props {
  /** 已关联的 MCP 服务编码列表 */
  modelValue?: string[];
  placeholder?: string;
  size?: 'default' | 'large' | 'small';
  clearable?: boolean;
}

const loading = ref(false);
const options = ref<AiMcpServerApi.ServerItem[]>([]);

function labelOf(item: AiMcpServerApi.ServerItem) {
  return `${item.name || item.serverCode}（${item.serverCode}）`;
}

async function search(keyword?: string) {
  loading.value = true;
  try {
    const res = await getAiMcpServerPageApi({
      pageNum: 1,
      pageSize: 100,
      keyword: keyword || undefined,
      // 仅启用（status=1）的服务供选择
      status: 1,
    });
    options.value = res.records ?? [];
  } finally {
    loading.value = false;
  }
}

/** 回显：已选但不在选项里的编码，用 keyword 兜底查回并 pin 进选项 */
async function ensureOptions(codes: string[]) {
  const missing = codes.filter(
    (code) => code && !options.value.some((o) => o.serverCode === code),
  );
  if (missing.length === 0) return;
  const results = await Promise.all(
    missing.map((code) =>
      getAiMcpServerPageApi({ pageNum: 1, pageSize: 100, keyword: code }),
    ),
  );
  const hits = results
    .flatMap((res) => res.records ?? [])
    .filter((o) => missing.includes(o.serverCode));
  if (hits.length > 0) {
    const known = new Set(options.value.map((o) => o.serverCode));
    options.value = [
      ...hits.filter((o) => !known.has(o.serverCode)),
      ...options.value,
    ];
  }
}

function onChange(value: string[]) {
  emit('update:modelValue', value);
}

watch(
  () => props.modelValue,
  (val) => {
    if (val && val.length > 0) ensureOptions(val);
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
    collapse-tags
    collapse-tags-tooltip
    filterable
    multiple
    remote
    remote-show-suffix
    style="width: 100%"
    @change="onChange"
  >
    <ElOption
      v-for="item in options"
      :key="item.serverCode"
      :label="labelOf(item)"
      :value="item.serverCode"
    />
  </ElSelect>
</template>
