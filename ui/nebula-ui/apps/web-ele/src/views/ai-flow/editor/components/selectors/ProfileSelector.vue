<script lang="ts" setup>
import type { AiModelProfileApi } from '#/api';

import { computed, onMounted, ref, watch } from 'vue';

import { ElOption, ElSelect } from 'element-plus';

import { getAiModelProfilePageApi } from '#/api';

defineOptions({ name: 'ProfileSelector' });

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '选择模型档案',
  size: 'default',
  clearable: true,
});

const emit = defineEmits<{
  change: [item: AiModelProfileApi.ProfileItem | undefined];
  'update:modelValue': [value: string];
}>();

interface Props {
  modelValue?: string;
  placeholder?: string;
  size?: 'default' | 'large' | 'small';
  clearable?: boolean;
}

const loading = ref(false);
const options = ref<AiModelProfileApi.ProfileItem[]>([]);

function labelOf(item: AiModelProfileApi.ProfileItem) {
  const parts = [item.provider, item.model].filter(Boolean).join('/');
  return `${item.name || item.profileCode}${parts ? `（${parts}）` : ''}`;
}

async function search(keyword?: string) {
  loading.value = true;
  try {
    const res = await getAiModelProfilePageApi({
      pageNum: 1,
      pageSize: 100,
      keyword: keyword || undefined,
    });
    options.value = res.records ?? [];
  } finally {
    loading.value = false;
  }
}

/** 回显：当前值不在选项里时，用 keyword=code 兜底查一次并 pin 进选项 */
async function ensureOption(code: string) {
  if (!code) return;
  if (options.value.some((o) => o.profileCode === code)) return;
  const res = await getAiModelProfilePageApi({
    pageNum: 1,
    pageSize: 100,
    keyword: code,
  });
  const hit = (res.records ?? []).find((o) => o.profileCode === code);
  if (hit) options.value = [hit, ...options.value];
}

/** 用可写 computed 承接 v-model，选中即回写并触发 change */
const selected = computed<string>({
  get: () => props.modelValue ?? '',
  set: (value) => {
    emit('update:modelValue', value);
    emit(
      'change',
      options.value.find((o) => o.profileCode === value),
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
  >
    <ElOption
      v-for="item in options"
      :key="item.profileCode"
      :label="labelOf(item)"
      :value="item.profileCode"
    />
  </ElSelect>
</template>
