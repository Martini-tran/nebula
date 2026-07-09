<script lang="ts" setup>
import type { StartInputParam } from '../start-input';

import type { AiFlowApi } from '#/api';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadioButton,
  ElRadioGroup,
} from 'element-plus';

import RunInputForm from './RunInputForm.vue';

defineOptions({ name: 'RunPanel' });

const props = defineProps<{
  /** 开始节点定义的入参（有定义时默认走动态表单，否则退回 JSON 文本框） */
  startInputs?: StartInputParam[];
}>();

const emit = defineEmits<{
  /** 请求执行：调用方先 save 再 run，回填 result */
  execute: [input: Record<string, any>];
  /** 请求续跑 */
  resume: [runId: string];
  /** 结果变化（用于画布高亮） */
  runFinished: [result: AiFlowApi.FlowRunResultRaw];
}>();

const visible = defineModel<boolean>('visible', { default: false });

const running = ref(false);
const runInputText = ref('{\n  \n}');
const result = ref<AiFlowApi.FlowRunResultRaw>();
const formRef = ref<InstanceType<typeof RunInputForm>>();

// ---------------- 入参模式：表单 / JSON ----------------
const formInputs = computed(() =>
  (props.startInputs ?? []).filter((it) => it.key),
);
const hasForm = computed(() => formInputs.value.length > 0);

const mode = ref<'form' | 'json'>('json');
watch(
  hasForm,
  (v) => {
    mode.value = v ? 'form' : 'json';
  },
  { immediate: true },
);

/** 模式互转：切 JSON 带出表单快照；切表单把 JSON 合并回控件 */
function onModeChange(next: 'form' | 'json') {
  if (next === 'json') {
    runInputText.value = JSON.stringify(
      formRef.value?.snapshot() ?? {},
      null,
      2,
    );
    return;
  }
  const text = runInputText.value.trim();
  if (!text) return;
  try {
    const obj = JSON.parse(text);
    if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
      formRef.value?.merge(obj);
    }
  } catch {
    // JSON 非法则不回填，保留表单原值
  }
}

// ---------------- 执行 / 续跑 ----------------
/** 由父组件在 execute/resume 完成后回填结果 */
function setResult(res: AiFlowApi.FlowRunResultRaw | undefined) {
  result.value = res;
  if (res) emit('runFinished', res);
}
function setRunning(v: boolean) {
  running.value = v;
}

function parseJsonInput(): null | Record<string, any> {
  try {
    return runInputText.value.trim() ? JSON.parse(runInputText.value) : {};
  } catch {
    ElMessage.error('input 不是合法 JSON');
    return null;
  }
}

function onExecute() {
  if (mode.value === 'form') {
    const collected = formRef.value?.collect();
    if (!collected?.ok) {
      ElMessage.warning(collected?.message ?? '入参校验未通过');
      return;
    }
    emit('execute', collected.input ?? {});
    return;
  }
  const input = parseJsonInput();
  if (input === null) return;
  emit('execute', input);
}

function onResume() {
  if (result.value?.runId) emit('resume', result.value.runId);
}

// ---------------- 结果展示 ----------------
/** 分节点结果：按 nodeResults 的 key 顺序（=执行顺序）展开 */
const nodeEntries = computed(() =>
  Object.entries(result.value?.nodeResults ?? {}),
);

const attributesText = computed(() =>
  result.value ? JSON.stringify(result.value.attributes ?? {}, null, 2) : '',
);

function stringify(v: any) {
  return typeof v === 'string' ? v : JSON.stringify(v, null, 2);
}

defineExpose({ setResult, setRunning });
</script>

<template>
  <ElDrawer v-model="visible" :size="560" direction="rtl" title="运行流程">
    <!-- 入参：有定义走动态表单（可切 JSON），无定义退回 JSON 文本框 -->
    <div class="mb-2 flex items-center justify-between">
      <span class="text-sm font-medium">运行入参</span>
      <ElRadioGroup
        v-if="hasForm"
        v-model="mode"
        size="small"
        @change="onModeChange($event as 'form' | 'json')"
      >
        <ElRadioButton value="form">表单</ElRadioButton>
        <ElRadioButton value="json">JSON</ElRadioButton>
      </ElRadioGroup>
    </div>

    <!-- 表单常驻（v-show 切换）：模式互转时 formRef 始终可用，草稿也不丢 -->
    <RunInputForm
      v-if="hasForm"
      v-show="mode === 'form'"
      ref="formRef"
      :inputs="formInputs"
      class="mb-3"
    />
    <ElForm v-show="!hasForm || mode === 'json'" label-width="0">
      <ElFormItem>
        <ElInput
          v-model="runInputText"
          :rows="6"
          placeholder="JSON 对象，作为初始上下文输入"
          type="textarea"
        />
      </ElFormItem>
    </ElForm>

    <div class="mb-3 flex gap-2">
      <ElButton :loading="running" type="primary" @click="onExecute">
        执行
      </ElButton>
      <ElButton v-if="result?.runId" :loading="running" @click="onResume">
        从断点续跑
      </ElButton>
    </div>

    <template v-if="result">
      <div v-if="result.runId" class="mb-2 text-xs text-gray-400">
        runId: {{ result.runId }}
      </div>

      <div class="mb-1 text-sm font-medium">节点执行轨迹</div>
      <ElCollapse class="mb-4">
        <ElCollapseItem
          v-for="[nodeCode, output] in nodeEntries"
          :key="nodeCode"
          :title="nodeCode"
          :name="nodeCode"
        >
          <pre
            class="max-h-[30vh] overflow-auto rounded bg-[#f5f5f5] p-2 text-xs dark:bg-[#2a2a2a]"
            >{{ stringify(output) }}</pre>
        </ElCollapseItem>
      </ElCollapse>

      <div class="mb-1 text-sm font-medium">上下文产物 (attributes)</div>
      <pre
        class="max-h-[40vh] overflow-auto rounded bg-[#f5f5f5] p-3 text-xs dark:bg-[#2a2a2a]"
        >{{ attributesText }}</pre>
    </template>
  </ElDrawer>
</template>
