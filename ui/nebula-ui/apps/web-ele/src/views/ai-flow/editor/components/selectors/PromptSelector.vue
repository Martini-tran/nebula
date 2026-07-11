<script lang="ts" setup>
/**
 * 提示词选择器（引用「提示词管理」的 ai_prompt 条目）。
 *
 * 与 ProfileSelector 同构：v-model 绑 promptCode，分页拉取候选、按 keyword
 * 过滤、回显时用 code 兜底查一次并 pin。选中触发 change 抛出整条提示词，
 * 供上层把 content 带出填入对应文本框；清空则抛 undefined。
 *
 * role：限定角色（system / user / assistant），只列该角色的提示词。
 */
import type { AiPromptApi } from '#/api';

import { computed, onMounted, ref, watch } from 'vue';

import { ElOption, ElSelect } from 'element-plus';

import { getAiPromptPageApi } from '#/api';

defineOptions({ name: 'PromptSelector' });

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '选择提示词',
  size: 'default',
  clearable: true,
  role: undefined,
});

const emit = defineEmits<{
  change: [item: AiPromptApi.PromptItem | undefined];
  'update:modelValue': [value: string];
}>();

interface Props {
  modelValue?: string;
  placeholder?: string;
  size?: 'default' | 'large' | 'small';
  clearable?: boolean;
  /** 限定角色，只列该角色的提示词 */
  role?: string;
}

const loading = ref(false);
const options = ref<AiPromptApi.PromptItem[]>([]);

function labelOf(item: AiPromptApi.PromptItem) {
  return `${item.name || item.promptCode}（${item.promptCode}）`;
}

async function search(keyword?: string) {
  loading.value = true;
  try {
    const res = await getAiPromptPageApi({
      pageNum: 1,
      pageSize: 100,
      keyword: keyword || undefined,
      role: props.role,
    });
    options.value = res.records ?? [];
  } finally {
    loading.value = false;
  }
}

/** 回显：当前值不在选项里时，用 keyword=code 兜底查一次并 pin 进选项 */
async function ensureOption(code: string) {
  if (!code) return;
  if (options.value.some((o) => o.promptCode === code)) return;
  const res = await getAiPromptPageApi({
    pageNum: 1,
    pageSize: 100,
    keyword: code,
    role: props.role,
  });
  const hit = (res.records ?? []).find((o) => o.promptCode === code);
  if (hit) options.value = [hit, ...options.value];
}

/** 用可写 computed 承接 v-model，选中即回写并触发 change */
const selected = computed<string>({
  get: () => props.modelValue ?? '',
  set: (value) => {
    emit('update:modelValue', value);
    emit(
      'change',
      options.value.find((o) => o.promptCode === value),
    );
  },
});

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
    v-model="selected"
    :clearable="props.clearable"
    :loading="loading"
    :placeholder="props.placeholder"
    :size="props.size"
    filterable
    remote
    :remote-method="search"
  >
    <ElOption
      v-for="item in options"
      :key="item.promptCode"
      :label="labelOf(item)"
      :value="item.promptCode"
    />
  </ElSelect>
</template>
