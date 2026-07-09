<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { AiAgentApi } from '#/api';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
} from 'element-plus';

import { createAgentApi } from '#/api';

import ProfileSelector from './selectors/ProfileSelector.vue';

/**
 * 根据流程派生智能体（ai_agent 定义，1 Flow : N Agent）。
 * 复用 createAgentApi：把传入的流程作为 Agent 引用的编排图，预填
 * flowCode/flowVersion/defaultProfileCode/name/description；用户主要补充
 * agentCode（跨版本稳定的记忆隔离键）及可选 IO 契约 / 记忆配置。
 * 派生的是 Agent 定义（非实例），去「智能体管理」页运行。
 *
 * 列表页与画布编辑页共用本组件，通过 open() 传入源流程信息。
 */
defineOptions({ name: 'DeriveAgentDialog' });

const emit = defineEmits<{
  /** 派生成功（宿主可据此刷新列表 / 提示） */
  created: [agentCode: string];
}>();

export interface DeriveSourceFlow {
  flowCode: string;
  name?: string;
  description?: string;
  version?: number;
  defaultProfileCode?: string;
}

const visible = ref(false);
const loading = ref(false);
const formRef = ref<FormInstance>();
const sourceLabel = ref('');

const form = reactive<{
  agentCode: string;
  defaultProfileCode: string;
  description: string;
  flowCode: string;
  flowVersion: null | number;
  inputSchema: string;
  memoryConfig: string;
  name: string;
  outputSchema: string;
}>({
  agentCode: '',
  defaultProfileCode: '',
  description: '',
  flowCode: '',
  flowVersion: 1,
  inputSchema: '',
  memoryConfig: '',
  name: '',
  outputSchema: '',
});

const rules: FormRules = {
  agentCode: [
    { required: true, message: '请输入 Agent 编码', trigger: 'blur' },
    { max: 64, message: '最多 64 个字符', trigger: 'blur' },
  ],
  flowCode: [{ required: true, message: '缺少引用的流程编码', trigger: 'blur' }],
  name: [{ max: 128, message: '最多 128 个字符', trigger: 'blur' }],
};

function open(flow: DeriveSourceFlow) {
  sourceLabel.value = flow.name || flow.flowCode;
  form.agentCode = flow.flowCode;
  form.name = flow.name ?? '';
  form.description = flow.description ?? '';
  form.flowCode = flow.flowCode;
  form.flowVersion = flow.version ?? 1;
  form.defaultProfileCode = flow.defaultProfileCode ?? '';
  form.inputSchema = '';
  form.outputSchema = '';
  form.memoryConfig = '';
  formRef.value?.clearValidate();
  visible.value = true;
}

defineExpose({ open });

/** 校验一个可选 JSON 文本字段（空视为合法），非法时提示并返回 false */
function validJson(text: string, label: string): boolean {
  const t = text.trim();
  if (!t) return true;
  try {
    JSON.parse(t);
    return true;
  } catch {
    ElMessage.error(`${label} 不是合法 JSON`);
    return false;
  }
}

async function submit() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  if (!validJson(form.memoryConfig, '记忆配置')) return;
  if (!validJson(form.inputSchema, '输入契约')) return;
  if (!validJson(form.outputSchema, '输出契约')) return;

  loading.value = true;
  try {
    const payload: AiAgentApi.AgentSaveRequest = {
      agentCode: form.agentCode,
      name: form.name || undefined,
      description: form.description || undefined,
      flowCode: form.flowCode,
      flowVersion: form.flowVersion ?? 1,
      inputSchema: form.inputSchema || undefined,
      outputSchema: form.outputSchema || undefined,
      memoryConfig: form.memoryConfig || undefined,
      defaultProfileCode: form.defaultProfileCode || undefined,
      version: 1,
      status: 1,
    };
    await createAgentApi(payload);
    ElMessage.success('智能体已生成，可前往「智能体管理」页运行');
    visible.value = false;
    emit('created', form.agentCode);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <ElDialog
    v-model="visible"
    :close-on-click-modal="false"
    :title="`派生智能体 · ${sourceLabel}`"
    width="680"
  >
    <ElForm ref="formRef" :model="form" :rules="rules" label-width="110px">
      <ElFormItem label="Agent 编码" prop="agentCode">
        <ElInput
          v-model="form.agentCode"
          maxlength="64"
          placeholder="跨版本稳定的逻辑标识（= 记忆隔离键）"
        />
      </ElFormItem>
      <ElFormItem label="名称">
        <ElInput v-model="form.name" maxlength="128" />
      </ElFormItem>
      <ElFormItem label="描述">
        <ElInput
          v-model="form.description"
          :rows="2"
          maxlength="512"
          type="textarea"
        />
      </ElFormItem>
      <ElFormItem label="引用流程" prop="flowCode">
        <ElInput v-model="form.flowCode" disabled />
      </ElFormItem>
      <ElFormItem label="流程版本">
        <ElInputNumber v-model="form.flowVersion" :min="1" />
      </ElFormItem>
      <ElFormItem label="默认档案">
        <ProfileSelector
          v-model="form.defaultProfileCode"
          class="w-full"
          placeholder="节点/Flow 未指定档案时使用（可选）"
        />
      </ElFormItem>
      <ElFormItem label="记忆配置">
        <ElInput
          v-model="form.memoryConfig"
          :rows="4"
          placeholder='JSON，如 {"enabled":true,"import":["userProfile"],"exportStrategy":"Append"}'
          type="textarea"
        />
      </ElFormItem>
      <ElFormItem label="输入契约">
        <ElInput
          v-model="form.inputSchema"
          :rows="3"
          placeholder="输入 JSON Schema（可选）"
          type="textarea"
        />
      </ElFormItem>
      <ElFormItem label="输出契约">
        <ElInput
          v-model="form.outputSchema"
          :rows="3"
          placeholder="输出 JSON Schema（可选）"
          type="textarea"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton :loading="loading" type="primary" @click="submit">
        生成
      </ElButton>
    </template>
  </ElDialog>
</template>
