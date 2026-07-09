<script lang="ts" setup>
/**
 * Agent（子 Agent 定义）选择器。
 *
 * 语义：AGENT 节点「调用另一个已设计好的 Agent（复用 Workflow）」，此处选被调 Agent 的
 * agentCode（Agent 定义，含自己的记忆/IO 契约），与后端 AgentNodeExecutor 读的 nodeConfig.refAgentCode 一致。
 * 数据源为 /admin/ai-agent/agents 分页列表；保留 allow-create，用户可直接输入 agentCode 兜底。
 */
import { onMounted, ref, watch } from 'vue';

import { ElOption, ElSelect } from 'element-plus';

import { getAgentPageApi } from '#/api';

defineOptions({ name: 'AgentSelector' });

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '选择要调用的 Agent',
  size: 'default',
  clearable: true,
});

const emit = defineEmits<{
  change: [item: AgentOption | undefined];
  'update:modelValue': [value: string];
}>();

interface Props {
  modelValue?: string;
  placeholder?: string;
  size?: 'default' | 'large' | 'small';
  clearable?: boolean;
}

/** 下拉选项（由 Agent 定义列表填充）；id 供调用方拉详情（allow-create 手输项无 id） */
export interface AgentOption {
  agentCode: string;
  name?: string;
  id?: number | string;
}

const loading = ref(false);
const options = ref<AgentOption[]>([]);

function labelOf(item: AgentOption) {
  return item.name ? `${item.name}（${item.agentCode}）` : item.agentCode;
}

/** 远程拉取可选子 Agent 列表（agentCode + name） */
async function loadOptions(keyword?: string) {
  loading.value = true;
  try {
    const res = await getAgentPageApi({ pageNum: 1, pageSize: 50, keyword });
    options.value = (res.records ?? []).map((r) => ({
      agentCode: r.agentCode,
      id: r.id,
      name: r.name,
    }));
    // 回显 pin：当前值不在结果里时补一条占位项，保证已选值可见
    if (props.modelValue) ensureOption(props.modelValue);
  } finally {
    loading.value = false;
  }
}

/** 回显：当前值不在选项里时，pin 一条占位项，保证已选值可见 */
function ensureOption(code: string) {
  if (!code) return;
  if (options.value.some((o) => o.agentCode === code)) return;
  options.value = [{ agentCode: code }, ...options.value];
}

function onChange(value: string) {
  emit('update:modelValue', value);
  emit(
    'change',
    options.value.find((o) => o.agentCode === value),
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
  loadOptions();
});
</script>

<template>
  <ElSelect
    :model-value="props.modelValue"
    allow-create
    :clearable="props.clearable"
    default-first-option
    filterable
    :loading="loading"
    :placeholder="props.placeholder"
    :remote-method="loadOptions"
    remote
    :size="props.size"
    @change="onChange"
  >
    <ElOption
      v-for="item in options"
      :key="item.agentCode"
      :label="labelOf(item)"
      :value="item.agentCode"
    />
  </ElSelect>
</template>
