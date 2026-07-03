<script lang="ts" setup>
/**
 * Agent（已保存流程）选择器。
 *
 * 语义：AGENT 节点要「调用另一个已设计好的 Agent（复用 Workflow）」，此处选被调
 * Agent 的 flowCode。下拉在语义上需要「已保存流程」列表——但后端整体待重构、当前
 * 接口不可用，故数据源暂为前端占位空列表，并开启 allow-create：用户可直接输入
 * flowCode 作为值兜底。待后端重构完成后，把 loadOptions 接上 getFlowPageApi 即可，
 * 组件结构（远程搜索 / 回显 pin）已就绪。
 */
import { onMounted, ref, watch } from 'vue';

import { ElOption, ElSelect } from 'element-plus';

defineOptions({ name: 'AgentSelector' });

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '选择要调用的 Agent（流程编码）',
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

/** 下拉选项（后端重构后由流程列表填充） */
export interface AgentOption {
  flowCode: string;
  name?: string;
}

const loading = ref(false);
const options = ref<AgentOption[]>([]);

function labelOf(item: AgentOption) {
  return item.name ? `${item.name}（${item.flowCode}）` : item.flowCode;
}

/**
 * 加载可选 Agent 列表。
 * TODO(后端重构后)：接 getFlowPageApi({ pageNum, pageSize, keyword }) 拉已保存流程。
 * 当前后端不可用，返回空列表——用户可通过 allow-create 直接输入 flowCode。
 */
async function loadOptions(_keyword?: string) {
  loading.value = true;
  try {
    options.value = [];
  } finally {
    loading.value = false;
  }
}

/** 回显：当前值不在选项里时，pin 一条占位项，保证已选值可见 */
function ensureOption(code: string) {
  if (!code) return;
  if (options.value.some((o) => o.flowCode === code)) return;
  options.value = [{ flowCode: code }, ...options.value];
}

function onChange(value: string) {
  emit('update:modelValue', value);
  emit(
    'change',
    options.value.find((o) => o.flowCode === value),
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
      :key="item.flowCode"
      :label="labelOf(item)"
      :value="item.flowCode"
    />
  </ElSelect>
</template>
