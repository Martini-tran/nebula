<script lang="ts" setup>
import type { AiFlowApi } from '#/api';

import { computed, ref } from 'vue';

import {
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

defineOptions({ name: 'RunPanel' });

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

/** 由父组件在 execute/resume 完成后回填结果 */
function setResult(res: AiFlowApi.FlowRunResultRaw | undefined) {
  result.value = res;
  if (res) emit('runFinished', res);
}
function setRunning(v: boolean) {
  running.value = v;
}

function parseInput(): null | Record<string, any> {
  try {
    return runInputText.value.trim() ? JSON.parse(runInputText.value) : {};
  } catch {
    ElMessage.error('input 不是合法 JSON');
    return null;
  }
}

function onExecute() {
  const input = parseInput();
  if (input === null) return;
  emit('execute', input);
}

function onResume() {
  if (result.value?.runId) emit('resume', result.value.runId);
}

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
    <ElForm label-width="60px">
      <ElFormItem label="input">
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
      <ElButton
        v-if="result?.runId"
        :loading="running"
        @click="onResume"
      >
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
