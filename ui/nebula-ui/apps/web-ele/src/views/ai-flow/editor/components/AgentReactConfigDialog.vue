<script lang="ts" setup>
/**
 * AGENT_REACT 节点配置弹窗（ReAct 里程碑执行节点）。
 *
 * 对应后端 AgentReactNodeExecutor：给模型一批工具白名单，模型在里程碑内**自主多轮
 * 选调不同工具**（ReAct loop），区别于 TOOL 节点的「固定单工具」。
 *
 * 由画布上 AGENT_REACT 节点右键菜单触发，或在 NodeConfigPanel 内嵌展示。
 * 两个 section：
 *   basic（模型档案 + 工具白名单 + 输出）/ prompt（系统提示词 + 用户提示模板）
 *
 * ⚠ 与 LlmConfigDialog / ToolConfigDialog 的关键差异：本弹窗**不只落嵌套配置**。
 * 确认时除写 nodeConfig.agentReact（前端回显用），还用 toBackendFields 同步出后端
 * 执行器真正读取的扁平契约——nodeConfig.toolCodes + 节点顶层 promptTemplate /
 * systemPrompt / outputMode / outputKey / profileCode。故画布配完即可直接运行，
 * 无需再手写 SQL 补字段。详见 ../agent-react-config。
 */
import type { Node } from '@antv/x6';
import type { FormInstance, FormRules } from 'element-plus';

import type { AgentReactConfig } from '../agent-react-config';

import type { AiPromptApi } from '#/api';

import { computed, nextTick, reactive, ref } from 'vue';

import {
  ElButton,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import {
  AGENT_REACT_OUTPUT_TYPES,
  defaultAgentReactConfig,
  normalizeAgentReactConfig,
  serializeAgentReactConfig,
  toBackendFields,
} from '../agent-react-config';
import { FLOW_DIALOG } from '../constants';
import { refreshNodeCard } from '../shapes/registerShapes';
import EmbeddableDialog from './EmbeddableDialog.vue';
import PromptBodyDialog from './PromptBodyDialog.vue';
import ProfileSelector from './selectors/ProfileSelector.vue';
import PromptSelector from './selectors/PromptSelector.vue';
import ToolSelector from './selectors/ToolSelector.vue';

defineOptions({ name: 'AgentReactConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** 确认写回节点后通知外层（NodeConfigPanel → 触发流程保存接口） */
const emit = defineEmits<{ apply: [] }>();

/** AGENT_REACT 节点主题色（与卡片描边一致，紫色系：自主决策类节点） */
const AGENT_REACT_THEME_COLOR = '#722ed1';

/**
 * 配置分区（对应右键菜单两项）：
 * - basic：模型档案 + 工具白名单 + 输出
 * - prompt：系统提示词 + 用户提示模板
 */
type AgentReactSection = 'basic' | 'prompt';

const visible = ref(false);
const section = ref<AgentReactSection>('basic');
/** ElTabs v-model 中转：其回传 string|number，这里收敛回 AgentReactSection */
const activeTab = computed<string>({
  get: () => section.value,
  set: (val) => {
    section.value = val === 'prompt' ? 'prompt' : 'basic';
  },
});
let target: Node | undefined;

/** 配置属性草稿：名称 + AGENT_REACT 各模块配置 */
const nameDraft = ref('');
const draft = reactive<AgentReactConfig>(defaultAgentReactConfig());

/** ElForm 实例：提交时触发校验；校验挂在 draft 上（:model="draft"） */
const formRef = ref<FormInstance>();

/**
 * 校验规则：模型档案必填（ReAct loop 每轮都要调模型，没档案跑不起来）。
 * 工具白名单不设必填——留空时后端退化为普通对话，是合法降级路径。
 * 懒校验：初始不报错，仅 validate() / change 后才显示。
 */
const rules: FormRules = {
  'model.profileCode': [
    { required: true, message: '请选择模型档案', trigger: 'change' },
  ],
};

/** 系统 / 用户提示词正文编辑弹窗实例，点击预览框时 open() 弹出 */
const systemBodyDialog = ref<InstanceType<typeof PromptBodyDialog>>();
const userBodyDialog = ref<InstanceType<typeof PromptBodyDialog>>();

/** 用 Object.assign 把归一化后的配置覆盖进 reactive 草稿（保持响应性） */
function applyDraft(cfg: AgentReactConfig) {
  Object.assign(draft, cfg);
}

/**
 * 打开：读入目标节点当前配置为草稿。normalize 会在嵌套结构缺字段时回迁后端扁平
 * 契约（nodeConfig.toolCodes / 顶层 promptTemplate 等），故手写 SQL 落的节点也能回显。
 */
function open(node: Node, target_section: AgentReactSection = 'basic') {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  nameDraft.value = (data.name as string) ?? '';
  applyDraft(normalizeAgentReactConfig(data.nodeConfig?.agentReact, data));
  section.value = target_section;
  visible.value = true;
  // 清掉上次遗留的校验态：初始不报错（DOM 就绪后再清）
  nextTick(() => formRef.value?.clearValidate());
}

/** 选中系统提示词：把正文带出填入文本框（可再手动覆盖） */
function onSystemPromptChange(item: AiPromptApi.PromptItem | undefined) {
  if (item?.content != null) draft.prompt.systemPrompt = item.content;
}

/** 选中用户提示词：把正文带出填入用户提示模板（可再手动覆盖） */
function onUserPromptChange(item: AiPromptApi.PromptItem | undefined) {
  if (item?.content != null) draft.prompt.userPromptTemplate = item.content;
}

/**
 * 保存：校验通过后**双写**——
 * 1. nodeConfig.agentReact：嵌套结构，供前端回显与卡片摘要（单一事实源）；
 * 2. nodeConfig.toolCodes + 节点顶层字段：后端 AgentReactNodeExecutor 真正读取的扁平契约。
 * 后者是前者的投影（toBackendFields），两份始终由同一次保存同步写出，不会漂移。
 */
async function handleConfirm() {
  const ok = await formRef.value?.validate().catch(() => false);
  if (ok === false) {
    section.value = 'basic'; // 必填项（模型档案）在基础配置里，切过去让报错可见
    return;
  }
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const backend = toBackendFields(draft);
    const nodeConfig: Record<string, any> = {
      ...data.nodeConfig,
      // 前端回显结构
      agentReact: serializeAgentReactConfig(draft),
      // ★后端执行器读的工具白名单（扁平数组）
      toolCodes: backend.toolCodes,
    };
    target.setData(
      {
        ...data,
        name: nameDraft.value.trim(),
        // ★后端执行器读的节点顶层字段（与嵌套结构同步）
        profileCode: backend.profileCode,
        systemPrompt: backend.systemPrompt,
        promptTemplate: backend.promptTemplate,
        outputMode: backend.outputMode,
        outputKey: backend.outputKey,
        nodeConfig,
      },
      { overwrite: true },
    );
    refreshNodeCard(target);
  }
  emit('apply');
  visible.value = false;
}

function handleClose() {
  visible.value = false;
}

/** 定位到指定分组：直接切 Tab（右键菜单两项调用本方法） */
function focusSection(target_section: string) {
  section.value = target_section === 'prompt' ? 'prompt' : 'basic';
}

defineExpose({ focusSection, open });
</script>

<template>
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="FLOW_DIALOG.class"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      ref="formRef"
      class="prop-form"
      label-position="top"
      :model="draft"
      require-asterisk-position="right"
      :rules="rules"
      :style="{ '--type-color': AGENT_REACT_THEME_COLOR }"
      @submit.prevent
    >
      <ElTabs v-model="activeTab" class="react-tabs">
        <!-- 基础配置：名称 + 模型档案｜工具白名单｜输出 -->
        <ElTabPane label="基础配置" name="basic">
          <div class="prop-grid-2">
            <ElFormItem label="名称">
              <ElInput
                v-model="nameDraft"
                maxlength="64"
                placeholder="卡片标题，缺省显示「ReAct 执行」"
              />
            </ElFormItem>
            <ElFormItem label="模型档案" prop="model.profileCode">
              <ProfileSelector
                v-model="draft.model.profileCode"
                class="w-full"
                placeholder="选择模型档案"
              />
            </ElFormItem>
          </div>

          <ElDivider />

          <div class="prop-grid-1">
            <ElFormItem label="工具白名单">
              <ToolSelector
                v-model="draft.tools.toolCodes"
                class="w-full"
                multiple
                placeholder="选择模型可自主调用的工具（可多选，留空则退化为纯对话）"
              />
            </ElFormItem>
          </div>

          <ElDivider />

          <div class="prop-grid-2">
            <ElFormItem label="输出类型">
              <ElSelect v-model="draft.output.type" style="width: 100%">
                <ElOption
                  v-for="opt in AGENT_REACT_OUTPUT_TYPES"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="输出键">
              <ElInput
                v-model="draft.output.key"
                placeholder="留空用节点编码"
              />
            </ElFormItem>
          </div>
        </ElTabPane>

        <!-- 提示词：系统提示词 + 用户提示模板（正文点预览框弹窗编辑） -->
        <ElTabPane label="提示词" name="prompt">
          <div class="prop-grid-1">
            <ElFormItem label="系统提示词">
              <PromptSelector
                v-model="draft.prompt.systemPromptCode"
                class="w-full"
                placeholder="从提示词管理选择（system），带出正文到下方（可覆盖）"
                role="system"
                @change="onSystemPromptChange"
              />
            </ElFormItem>
            <ElFormItem class="prompt-body-item">
              <div
                class="prompt-preview"
                role="button"
                tabindex="0"
                @click="systemBodyDialog?.open()"
                @keydown.enter.prevent="systemBodyDialog?.open()"
              >
                <span
                  v-if="draft.prompt.systemPrompt"
                  class="prompt-preview-text"
                >
                  {{ draft.prompt.systemPrompt }}
                </span>
                <span v-else class="prompt-preview-empty">
                  点击编辑系统提示词正文
                </span>
                <span class="prompt-preview-edit">编辑</span>
              </div>
            </ElFormItem>

            <ElFormItem label="用户提示模板">
              <PromptSelector
                v-model="draft.prompt.userPromptCode"
                class="w-full"
                placeholder="从提示词管理选择（user），带出正文到下方（可覆盖）"
                role="user"
                @change="onUserPromptChange"
              />
            </ElFormItem>
            <ElFormItem class="prompt-body-item">
              <div
                class="prompt-preview"
                role="button"
                tabindex="0"
                @click="userBodyDialog?.open()"
                @keydown.enter.prevent="userBodyDialog?.open()"
              >
                <span
                  v-if="draft.prompt.userPromptTemplate"
                  class="prompt-preview-text"
                >
                  {{ draft.prompt.userPromptTemplate }}
                </span>
                <span v-else class="prompt-preview-empty">
                  点击编辑用户提示模板正文
                </span>
                <span class="prompt-preview-edit">编辑</span>
              </div>
            </ElFormItem>
          </div>
        </ElTabPane>
      </ElTabs>
    </ElForm>

    <PromptBodyDialog
      ref="systemBodyDialog"
      v-model="draft.prompt.systemPrompt"
      placeholder="系统提示词正文，如：你负责完成当前里程碑，可自主调用工具。"
      :rows="14"
      title="编辑系统提示词正文"
    />
    <PromptBodyDialog
      ref="userBodyDialog"
      v-model="draft.prompt.userPromptTemplate"
      placeholder="用户提示模板，支持 {{变量}} 占位符引用上游产物"
      :rows="16"
      title="编辑用户提示模板正文"
    />

    <template #footer>
      <ElButton v-if="!embedded" @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">
        {{ embedded ? '应用配置' : '保存配置' }}
      </ElButton>
    </template>
  </EmbeddableDialog>
</template>

<style scoped>
/* ===== 呼吸感：容器内边距 + 分组/表单项间距（与 LlmConfigDialog 同款） ===== */
.prop-form {
  padding: 8px 4px;
}

.prop-form :deep(.el-tabs__content) {
  padding: 8px 4px 4px;
}

.prop-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

/* 标签在上：标签与输入框间距 6-8px */
.prop-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}

/* 逻辑分组之间：分割线 + 32px 呼吸间距 */
.react-tabs :deep(.el-divider) {
  margin: 32px 0;
}

/* ===== 布局：长项单列 / 短项两列 ===== */
.prop-grid-1 {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.prop-grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
}

.prop-grid-1 :deep(.el-form-item),
.prop-grid-2 :deep(.el-form-item) {
  min-width: 0;
}

/* 提示词正文项紧贴上方选择器 */
.prompt-body-item {
  margin-top: -12px;
}

/* ===== 视觉层级：输入框统一高度 / 圆角 / 五态 ===== */
.prop-form :deep(.el-input__wrapper),
.prop-form :deep(.el-select__wrapper) {
  min-height: 42px;
  border-radius: 8px;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
}

.prop-form :deep(.el-input__wrapper:hover),
.prop-form :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-border-color-hover) inset;
}

.prop-form :deep(.el-input__wrapper.is-focus),
.prop-form :deep(.el-select__wrapper.is-focused) {
  box-shadow:
    0 0 0 1px var(--type-color, var(--el-color-primary)) inset,
    0 0 0 3px color-mix(in srgb, var(--type-color, var(--el-color-primary)) 20%, transparent);
}

.prop-form :deep(.el-form-item.is-error .el-input__wrapper),
.prop-form :deep(.el-form-item.is-error .el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

.prop-form :deep(.el-form-item__error) {
  padding-top: 4px;
  font-size: 12px;
}

.prop-form :deep(.el-input__inner::placeholder),
.prop-form :deep(.el-textarea__inner::placeholder) {
  color: var(--el-text-color-placeholder);
}

/* 提示词正文只读预览框：点击弹出编辑弹窗（与 LlmConfigDialog 同款） */
.prompt-preview {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  width: 100%;
  min-height: 42px;
  padding: 9px 12px;
  font-size: 13px;
  line-height: 1.5;
  cursor: pointer;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.prompt-preview:hover {
  border-color: var(--el-border-color-hover);
}

.prompt-preview:focus-visible {
  border-color: var(--type-color, var(--el-color-primary));
  outline: none;
  box-shadow: 0 0 0 3px
    color-mix(in srgb, var(--type-color, var(--el-color-primary)) 20%, transparent);
}

.prompt-preview-text {
  display: -webkit-box;
  flex: 1;
  overflow: hidden;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: pre-wrap;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.prompt-preview-empty {
  flex: 1;
  color: var(--el-text-color-placeholder);
}

.prompt-preview-edit {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--type-color, var(--el-color-primary));
}
</style>
