<script lang="ts" setup>
/**
 * 运行 Agent 弹窗：按定义的 input_schema 动态生成入参表单（复用编辑器的
 * RunInputForm，可切 JSON 模式），运行后就地展示结果；结果为 SUSPENDED 时
 * 直接在弹窗内选 awaitingEvents 中的事件做 signal 唤醒，可多轮直至终态。
 *
 * open(agent?)：带 agent 时锁定目标（定义页行内运行）；不带时弹出
 * AgentSelector 让用户先选（实例页工具栏运行）。
 */
import type { StartInputParam } from '../../ai-flow/editor/start-input';

import type { AiAgentApi } from '#/api';

import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTag,
} from 'element-plus';

import { getAgentDetailApi, runAgentApi, signalAgentInstanceApi } from '#/api';

import RunInputForm from '../../ai-flow/editor/components/RunInputForm.vue';
import AgentSelector from '../../ai-flow/editor/components/selectors/AgentSelector.vue';
import { fetchAgentDetailByCode } from '../agent-resolve';
import { schemaToStartInputs } from '../schema-form';

defineOptions({ name: 'AgentRunDialog' });

const emit = defineEmits<{
  /** run/signal 产生了新实例状态，父页可刷新列表 */
  finished: [result: AiAgentApi.AgentRunResult];
}>();

const router = useRouter();

const STATUS_TAG: Record<string, string> = {
  FAILED: 'danger',
  RUNNING: 'primary',
  SUCCESS: 'success',
  SUSPENDED: 'warning',
};

const visible = ref(false);
/** 是否需要先在弹窗里选 Agent（open 未带目标时） */
const needSelect = ref(false);
const agentCode = ref('');
const agentName = ref('');
const detailLoading = ref(false);

const formInputs = ref<StartInputParam[]>([]);
const hasForm = computed(() => formInputs.value.length > 0);
const mode = ref<'form' | 'json'>('json');
const formRef = ref<InstanceType<typeof RunInputForm>>();
const runInputText = ref('{\n  \n}');
const conversationId = ref('');

const running = ref(false);
const result = ref<AiAgentApi.AgentRunResult>();

// ---------------- 唤醒（结果为 SUSPENDED 时就地渲染） ----------------
const signalEvent = ref('');
const signalPayloadText = ref('{\n  \n}');
const signaling = ref(false);
const awaitingEvents = computed(() => result.value?.awaitingEvents ?? []);
const suspended = computed(() => result.value?.status === 'SUSPENDED');

const attributesText = computed(() =>
  result.value ? JSON.stringify(result.value.attributes ?? {}, null, 2) : '',
);

function reset() {
  needSelect.value = false;
  agentCode.value = '';
  agentName.value = '';
  formInputs.value = [];
  mode.value = 'json';
  runInputText.value = '{\n  \n}';
  conversationId.value = '';
  result.value = undefined;
  signalEvent.value = '';
  signalPayloadText.value = '{\n  \n}';
}

async function open(agent?: {
  agentCode?: string;
  id?: number | string;
  name?: string;
}) {
  reset();
  visible.value = true;
  if (agent?.agentCode) {
    agentCode.value = agent.agentCode;
    agentName.value = agent.name ?? '';
    await loadSchema(agent.id);
  } else {
    needSelect.value = true;
  }
}

/** 拉定义详情并按 inputSchema 生成表单；无 id 时按 code 解析 */
async function loadSchema(id?: number | string) {
  detailLoading.value = true;
  try {
    const detail = await (id === undefined
      ? fetchAgentDetailByCode(agentCode.value)
      : getAgentDetailApi(id));
    if (detail?.name) agentName.value = detail.name;
    formInputs.value = schemaToStartInputs(detail?.inputSchema);
    mode.value = hasForm.value ? 'form' : 'json';
  } catch {
    formInputs.value = [];
    mode.value = 'json';
  } finally {
    detailLoading.value = false;
  }
}

async function onSelectAgent(code: string) {
  agentCode.value = code;
  agentName.value = '';
  result.value = undefined;
  if (code) await loadSchema();
  else formInputs.value = [];
}

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

function collectInputs(): null | Record<string, any> {
  if (mode.value === 'form' && hasForm.value) {
    const collected = formRef.value?.collect();
    if (!collected?.ok) {
      ElMessage.warning(collected?.message ?? '入参校验未通过');
      return null;
    }
    return collected.input ?? {};
  }
  const text = runInputText.value.trim();
  if (!text) return {};
  try {
    const obj = JSON.parse(text);
    if (obj && typeof obj === 'object' && !Array.isArray(obj)) return obj;
    ElMessage.error('入参必须是 JSON 对象');
    return null;
  } catch {
    ElMessage.error('入参不是合法 JSON');
    return null;
  }
}

async function doRun() {
  if (!agentCode.value) {
    ElMessage.warning('请选择要运行的 Agent');
    return;
  }
  const inputs = collectInputs();
  if (inputs === null) return;
  running.value = true;
  try {
    const res = await runAgentApi(agentCode.value, {
      conversationId: conversationId.value || undefined,
      inputs,
    });
    applyResult(res);
  } finally {
    running.value = false;
  }
}

async function doSignal() {
  const id = result.value?.instanceId;
  if (!id) return;
  let payload: Record<string, any> = {};
  const text = signalPayloadText.value.trim();
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      ElMessage.error('payload 不是合法 JSON');
      return;
    }
  }
  signaling.value = true;
  try {
    const res = await signalAgentInstanceApi(id, {
      event: signalEvent.value || undefined,
      payload,
    });
    applyResult(res);
  } finally {
    signaling.value = false;
  }
}

function applyResult(res: AiAgentApi.AgentRunResult) {
  result.value = res;
  signalEvent.value = res.awaitingEvents?.[0] ?? '';
  signalPayloadText.value = '{\n  \n}';
  emit('finished', res);
}

function goInstances() {
  visible.value = false;
  router.push('/ai-agent/instances');
}

function goReplay() {
  const id = result.value?.instanceId;
  if (!id) return;
  visible.value = false;
  router.push({ name: 'AiAgentReplay', query: { instanceId: id } });
}

defineExpose({ open });
</script>

<template>
  <ElDialog
    v-model="visible"
    :close-on-click-modal="false"
    :title="agentName ? `运行 Agent · ${agentName}` : '运行 Agent'"
    width="620"
  >
    <ElForm label-width="96px">
      <ElFormItem v-if="needSelect" label="调用 Agent" required>
        <AgentSelector
          :model-value="agentCode"
          class="w-full"
          @update:model-value="onSelectAgent"
        />
      </ElFormItem>
      <ElFormItem v-else label="Agent">
        <span class="text-sm">{{ agentName || agentCode }}</span>
        <span v-if="agentName" class="ml-2 text-xs text-gray-400">
          {{ agentCode }}
        </span>
      </ElFormItem>

      <ElFormItem label="会话ID">
        <ElInput v-model="conversationId" placeholder="可选" />
      </ElFormItem>

      <ElFormItem label="入参">
        <div class="w-full">
          <div v-if="hasForm" class="mb-2 flex justify-end">
            <ElRadioGroup
              v-model="mode"
              size="small"
              @change="onModeChange($event as 'form' | 'json')"
            >
              <ElRadioButton value="form">表单</ElRadioButton>
              <ElRadioButton value="json">JSON</ElRadioButton>
            </ElRadioGroup>
          </div>
          <!-- 表单常驻（v-show 切换）：模式互转时 formRef 始终可用，草稿不丢 -->
          <RunInputForm
            v-if="hasForm"
            v-show="mode === 'form'"
            ref="formRef"
            :inputs="formInputs"
          />
          <ElInput
            v-show="!hasForm || mode === 'json'"
            v-model="runInputText"
            :rows="6"
            placeholder="JSON 对象，作为实例入参"
            type="textarea"
          />
        </div>
      </ElFormItem>
    </ElForm>

    <div class="mb-3 flex gap-2">
      <ElButton
        :disabled="detailLoading"
        :loading="running"
        type="primary"
        @click="doRun"
      >
        运行
      </ElButton>
    </div>

    <!-- 结果区 -->
    <template v-if="result">
      <div class="mb-2 flex items-center gap-2">
        <ElTag :type="(STATUS_TAG[result.status ?? ''] as any) || 'info'" size="small">
          {{ result.status }}
        </ElTag>
        <span v-if="result.currentState" class="text-xs text-gray-400">
          当前状态：{{ result.currentState }}
        </span>
      </div>
      <div v-if="result.instanceId" class="mb-2 text-xs text-gray-400">
        instanceId: {{ result.instanceId }}
      </div>

      <!-- SUSPENDED：就地唤醒 -->
      <div v-if="suspended" class="mb-3 rounded border border-[var(--el-border-color)] p-3">
        <div class="mb-2 text-sm font-medium">实例已挂起，等待事件</div>
        <ElForm label-width="72px">
          <ElFormItem label="事件">
            <ElSelect
              v-model="signalEvent"
              allow-create
              class="w-full"
              filterable
              placeholder="选择或输入事件名"
            >
              <ElOption
                v-for="e in awaitingEvents"
                :key="e"
                :label="e"
                :value="e"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="负载">
            <ElInput
              v-model="signalPayloadText"
              :rows="4"
              placeholder="JSON 对象，写入 context 供出边 guard 裁决"
              type="textarea"
            />
          </ElFormItem>
        </ElForm>
        <ElButton :loading="signaling" size="small" type="warning" @click="doSignal">
          唤醒
        </ElButton>
      </div>

      <div class="mb-1 text-sm font-medium">上下文产物 (attributes)</div>
      <pre
        class="max-h-[32vh] overflow-auto rounded bg-[#f5f5f5] p-3 text-xs dark:bg-[#2a2a2a]"
        >{{ attributesText }}</pre>
    </template>

    <template #footer>
      <ElButton
        v-if="result?.instanceId"
        link
        type="primary"
        @click="goReplay"
      >
        回放
      </ElButton>
      <ElButton v-if="result" link type="primary" @click="goInstances">
        查看实例
      </ElButton>
      <ElButton @click="visible = false">关闭</ElButton>
    </template>
  </ElDialog>
</template>
