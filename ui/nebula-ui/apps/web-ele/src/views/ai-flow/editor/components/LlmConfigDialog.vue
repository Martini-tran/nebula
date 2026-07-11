<script lang="ts" setup>
/**
 * LLM 节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上 LLM 节点右键菜单触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面按节点类型分发 → 本弹窗 open(node, section)。
 *
 * 右键菜单已按模块拆成三项（基础/模型/提示词），每项打开本弹窗并只渲染
 * 对应 section（标题随之变化），避免单个大 Tab 弹窗。三个 section：
 *   basic（名称 + 上下文）/ model（Model + 调用参数 + 输出）/ prompt
 * 配置结构与读写归一化集中在 ../llm-config（落库到 nodeConfig.llm）。
 *
 * 弹窗自持草稿：打开时从节点读入并补全缺省，确认时归一化写回并刷新卡片；
 * 取消丢弃草稿。名称与 llm 配置一并写回（无论打开哪个 section）。
 */
import type { Node } from '@antv/x6';
import type { FormInstance, FormRules } from 'element-plus';

import type { AiPromptApi } from '#/api';

import type { LlmConfig } from '../llm-config';

import { computed, nextTick, reactive, ref } from 'vue';

import {
  ElButton,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import { FLOW_DIALOG } from '../constants';
import EmbeddableDialog from './EmbeddableDialog.vue';
import {
  defaultLlmConfig,
  LLM_OUTPUT_TYPES,
  normalizeLlmConfig,
  serializeLlmConfig,
} from '../llm-config';
import { refreshNodeCard } from '../shapes/registerShapes';
import PromptBodyDialog from './PromptBodyDialog.vue';
import ProfileSelector from './selectors/ProfileSelector.vue';
import PromptSelector from './selectors/PromptSelector.vue';

defineOptions({ name: 'LlmConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** LLM 节点主题色（与 LlmNodeCard 卡片描边一致），驱动小节标题左边条 */
const LLM_THEME_COLOR = '#13c2c2';

/**
 * 配置分区（对应右键菜单两项）：
 * - basic：模型档案 + 提示词 + 输出
 * - advanced：上下文 + 调用参数
 */
type LlmSection = 'advanced' | 'basic';

/** 分区标题（弹窗 title 随打开的 section 变化） */
const SECTION_TITLES: Record<LlmSection, string> = {
  basic: '基础配置',
  advanced: '高级配置',
};

const visible = ref(false);
const section = ref<LlmSection>('basic');
const dialogTitle = computed(() => SECTION_TITLES[section.value]);
/** ElTabs v-model 中转：其回传 string|number，这里收敛回 LlmSection */
const activeTab = computed<string>({
  get: () => section.value,
  set: (val) => {
    section.value = val === 'advanced' ? 'advanced' : 'basic';
  },
});
let target: Node | undefined;

/** 配置属性草稿：LLM 各模块配置 */
const draft = reactive<LlmConfig>(defaultLlmConfig());

/** ElForm 实例：提交时触发校验；校验挂在 draft 上（:model="draft"） */
const formRef = ref<FormInstance>();

/**
 * 校验规则：模型档案必填（未选阻断保存，贴输入框下方红字）。
 * 初始不报错——ElForm 默认懒校验，仅在 validate() / blur 后才显示错误。
 */
const rules: FormRules = {
  'model.profileCode': [
    { required: true, message: '请选择模型档案', trigger: 'change' },
  ],
};

/** 系统 / 用户提示词正文编辑弹窗实例（各自独立），点击预览框时 open() 弹出 */
const systemBodyDialog = ref<InstanceType<typeof PromptBodyDialog>>();
const userBodyDialog = ref<InstanceType<typeof PromptBodyDialog>>();

/** 用 Object.assign 把归一化后的配置覆盖进 reactive 草稿（保持响应性） */
function applyDraft(cfg: LlmConfig) {
  Object.assign(draft, cfg);
}

/**
 * 打开弹窗：读入目标节点当前配置为草稿（缺省字段由 normalize 补全），
 * 并按 section 决定渲染哪一模块。缺省打开「基础配置」。
 */
function open(node: Node, target_section: LlmSection = 'basic') {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  applyDraft(normalizeLlmConfig(data.nodeConfig?.llm));
  section.value = target_section;
  visible.value = true;
  // 清掉上次遗留的校验态：初始不报错（DOM 就绪后再清）
  nextTick(() => formRef.value?.clearValidate());
}

/**
 * 选中系统提示词：把提示词正文带出填入系统提示词文本框（可再手动覆盖）。
 * 清空引用时保留已填正文不动，只清 systemPromptCode（由 v-model 完成）。
 */
function onSystemPromptChange(item: AiPromptApi.PromptItem | undefined) {
  if (item?.content != null) draft.prompt.systemPrompt = item.content;
}

/**
 * 选中用户提示词：把提示词正文带出填入用户提示模板（可再手动覆盖）。
 * 清空引用时保留已填正文不动，只清 userPromptCode（由 v-model 完成）。
 */
function onUserPromptChange(item: AiPromptApi.PromptItem | undefined) {
  if (item?.content != null) draft.prompt.userPromptTemplate = item.content;
}

/**
 * 保存：先校验（模型档案必填）。校验不过时切到基础配置 Tab 让红字可见并阻断；
 * 通过后归一化写回 nodeConfig.llm，同步顶层 profileCode，刷新卡片。
 */
async function handleConfirm() {
  const ok = await formRef.value?.validate().catch(() => false);
  if (ok === false) {
    section.value = 'basic'; // 必填项在基础配置里，切过去让报错可见
    return;
  }
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = {
      ...data.nodeConfig,
      llm: serializeLlmConfig(draft),
    };
    target.setData(
      {
        ...data,
        // 节点顶层 profileCode：供后端「节点 > Agent > Flow」三层定档，
        // 与 nodeConfig.llm.model.profileCode 保持同步（空则不引用档案）
        profileCode: draft.model.profileCode || undefined,
        nodeConfig,
      },
      { overwrite: true },
    );
    refreshNodeCard(target);
  }
  visible.value = false;
}

function handleClose() {
  visible.value = false;
}

/**
 * 定位到指定分组：直接切换到对应 Tab（基础/高级）。
 * 右键菜单两项即调用本方法，与 open 职责分离（open 负责 hydrate + 默认 Tab）。
 */
function focusSection(target: LlmSection) {
  section.value = target;
}

defineExpose({ focusSection, open });
</script>

<template>
  <!-- FLOW_DIALOG：流程编辑器弹窗统一规格；额外 llm-config-dialog 让弹窗可拖拽缩放 -->
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="`${FLOW_DIALOG.class} llm-config-dialog`"
    :title="dialogTitle"
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
      :style="{ '--type-color': LLM_THEME_COLOR }"
      @submit.prevent
    >
      <ElTabs v-model="activeTab" class="llm-tabs">
        <!-- 基础配置：模型档案 + 输出类型 一组，提示词一组（分割线区分） -->
        <ElTabPane label="基础配置" name="basic">
          <div class="prop-grid-2">
            <ElFormItem label="模型档案" prop="model.profileCode">
              <ProfileSelector
                v-model="draft.model.profileCode"
                class="w-full"
                placeholder="选择模型档案（模型、地址、密钥、参数均由档案承载）"
              />
            </ElFormItem>
            <ElFormItem label="输出类型">
              <ElSelect v-model="draft.output.type" style="width: 100%">
                <ElOption
                  v-for="opt in LLM_OUTPUT_TYPES"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
          </div>

          <ElDivider />
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
              <!-- 只读预览框：点击弹窗编辑正文 -->
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
            <ElFormItem label="用户提示词">
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

        <!-- 高级配置：上下文 + 调用参数（小节间用分割线区分） -->
        <ElTabPane label="高级配置" name="advanced">
          <div class="prop-grid-2">
            <ElFormItem label="历史消息">
              <ElSwitch v-model="draft.context.messages" />
              <span class="hint">携带历史消息</span>
            </ElFormItem>
            <ElFormItem label="记忆">
              <ElSwitch v-model="draft.context.memory" />
              <span class="hint">读取 Agent 记忆</span>
            </ElFormItem>
            <ElFormItem label="知识库">
              <ElSwitch v-model="draft.context.knowledge" />
              <span class="hint">使用知识库</span>
            </ElFormItem>
            <ElFormItem label="流程变量">
              <ElSwitch v-model="draft.context.variables" />
              <span class="hint">读取流程变量</span>
            </ElFormItem>
            <ElFormItem label="文件">
              <ElSwitch v-model="draft.context.artifacts" />
              <span class="hint">携带文件</span>
            </ElFormItem>
          </div>

          <ElDivider />
          <div class="prop-grid-2">
            <ElFormItem label="温度">
              <ElInputNumber
                v-model="draft.parameters.temperature"
                :max="2"
                :min="0"
                :step="0.1"
                controls-position="right"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem label="Top P 采样">
              <ElInputNumber
                v-model="draft.parameters.topP"
                :max="1"
                :min="0"
                :step="0.05"
                controls-position="right"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem label="最大 Token 数">
              <ElInputNumber
                v-model="draft.parameters.maxTokens"
                :min="1"
                controls-position="right"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem label="随机种子">
              <ElInputNumber
                v-model="draft.parameters.seed"
                controls-position="right"
                placeholder="可选"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem label="流式输出">
              <ElSwitch v-model="draft.parameters.stream" />
            </ElFormItem>
          </div>
        </ElTabPane>
      </ElTabs>
    </ElForm>

    <!-- 系统 / 用户提示词正文编辑弹窗（各自独立，点预览框弹出） -->
    <PromptBodyDialog
      ref="systemBodyDialog"
      v-model="draft.prompt.systemPrompt"
      placeholder="系统提示词正文"
      :rows="14"
      title="编辑系统提示词正文"
    />
    <PromptBodyDialog
      ref="userBodyDialog"
      v-model="draft.prompt.userPromptTemplate"
      placeholder="用户提示模板，支持 {{inputs.xxx}} 变量"
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
/* ===== 呼吸感：容器内边距 + 分组/表单项间距 ===== */

/* 整体表单容器充足内边距（≥24px），Tab 内容区同样留白 */
.prop-form {
  padding: 8px 4px;
}

.prop-form :deep(.el-tabs__content) {
  padding: 8px 4px 4px;
}

/* 表单项垂直间距 ≥20px */
.prop-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

/* 标签在上：标签与输入框间距 6-8px；标签字重/字号提升可读性 */
.prop-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}

/* 逻辑分组之间：分割线 + 32px 呼吸间距 */
.llm-tabs :deep(.el-divider) {
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

/* 提示词正文项紧贴上方选择器（去掉标签占位），间距收紧 */
.prompt-body-item {
  margin-top: -12px;
}

/* ===== 视觉层级：输入框统一高度 40-44px / 圆角 8px / 五态 ===== */

/* 文本输入 / 选择器 / 数字框的外壳统一规格 */
.prop-form :deep(.el-input__wrapper),
.prop-form :deep(.el-select__wrapper),
.prop-form :deep(.el-input-number) {
  min-height: 42px;
  border-radius: 8px;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
}

/* hover：边框中性灰略深 */
.prop-form :deep(.el-input__wrapper:hover),
.prop-form :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-border-color-hover) inset;
}

/* focus：主题色边框 + 外发光阴影 */
.prop-form :deep(.el-input__wrapper.is-focus),
.prop-form :deep(.el-select__wrapper.is-focused) {
  box-shadow:
    0 0 0 1px var(--type-color, var(--el-color-primary)) inset,
    0 0 0 3px color-mix(in srgb, var(--type-color, var(--el-color-primary)) 20%, transparent);
}

/* disabled：置灰、禁用光标 */
.prop-form :deep(.el-input.is-disabled .el-input__wrapper),
.prop-form :deep(.el-select__wrapper.is-disabled) {
  background: var(--el-disabled-bg-color);
  box-shadow: 0 0 0 1px var(--el-disabled-border-color) inset;
  cursor: not-allowed;
}

/* error：红框（error 提示文案由 el-form-item__error 贴底显示） */
.prop-form :deep(.el-form-item.is-error .el-input__wrapper),
.prop-form :deep(.el-form-item.is-error .el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

/* success：绿框（校验通过态，validate-status=success 时） */
.prop-form :deep(.el-form-item.is-success .el-input__wrapper),
.prop-form :deep(.el-form-item.is-success .el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-success) inset;
}

/* 错误提示：紧贴输入框下方的红色小字（禁用弹窗） */
.prop-form :deep(.el-form-item__error) {
  padding-top: 4px;
  font-size: 12px;
}

/* 占位符：比正文浅 2 级 */
.prop-form :deep(.el-input__inner::placeholder),
.prop-form :deep(.el-textarea__inner::placeholder) {
  color: var(--el-text-color-placeholder);
}

/* textarea 圆角对齐 */
.prop-form :deep(.el-textarea__inner) {
  border-radius: 8px;
}

/* 开关旁的说明文字 */
.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 提示词正文只读预览框：点击弹出编辑弹窗 */
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
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.prompt-preview:hover {
  border-color: var(--type-color, var(--el-color-primary));
}

.prompt-preview:focus-visible {
  outline: none;
  box-shadow:
    0 0 0 1px var(--type-color, var(--el-color-primary)) inset,
    0 0 0 3px color-mix(in srgb, var(--type-color, var(--el-color-primary)) 20%, transparent);
}

/* 正文文本：最多显示 3 行，超出省略 */
.prompt-preview-text {
  flex: 1;
  display: -webkit-box;
  overflow: hidden;
  color: var(--el-text-color-primary);
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

<!--
  非 scoped：ElDialog append-to-body 到 <body>，scoped 选择器穿透不到 .el-dialog。
  让 LLM 配置弹窗可由用户拖拽右下角自由缩放（宽 + 高）。
-->
<style>
/* 弹窗整体：flex column + resize，用户拖右下角自由缩放 */
.llm-config-dialog.el-dialog {
  display: flex;
  flex-direction: column;
  min-width: 480px;
  max-width: 96vw;
  min-height: 320px;
  max-height: 92vh;
  /* 拖拽缩放手柄在右下角；overflow 需非 visible 才能出现 resize 手柄 */
  overflow: hidden;
  resize: both;
}

/* header / footer 不参与伸缩，body 吃掉剩余高度并滚动 */
.llm-config-dialog.el-dialog .el-dialog__header,
.llm-config-dialog.el-dialog .el-dialog__footer {
  flex-shrink: 0;
}

/* 覆盖 flow-prop-dialog 的 max-height:78vh，让 body 随弹窗高度自适应 */
.llm-config-dialog.el-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
}
</style>
