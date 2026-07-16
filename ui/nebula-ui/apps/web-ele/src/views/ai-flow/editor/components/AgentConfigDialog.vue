<script lang="ts" setup>
/**
 * AGENT 节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上 AGENT 节点右键菜单「配置」触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面按节点类型分发 → 本弹窗 open(node)。
 *
 * AGENT 节点「调用另一个已设计好的 Agent（复用 Workflow）」，递归执行子 Agent。弹窗只配一件事：
 *   - 调用 Agent：选被调子 Agent 的 agentCode（Agent 定义）
 * 配置结构与读写归一化集中在 ../agent-config（平铺落库到 nodeConfig.refAgentCode），
 * 与后端 AgentNodeExecutor 严格一致。
 *
 * Input/Output Mapping 不在此弹窗编辑（子 Agent 入参走同名上下文，产物由后端兜底整体带回父 context）；
 * 归一化读入的存量 inputMapping/outputMapping 原样保留写回，不丢旧数据、不改后端契约。
 *
 * 卡片名称无独立字段：直接取所选 Agent 的名称写回 node.data.name（未选时留空，由卡片兜底显示「Agent」）。
 *
 * 弹窗自持草稿：打开时从节点读入并补全缺省（含旧结构兼容），确认时归一化平铺写回并刷新卡片；取消丢弃草稿。
 */
import type { Node } from '@antv/x6';
import type { FormInstance, FormRules } from 'element-plus';

import type { AgentConfig } from '../agent-config';
import type { AgentOption } from './selectors/AgentSelector.vue';

import { nextTick, reactive, ref } from 'vue';

import { ElButton, ElForm, ElFormItem } from 'element-plus';

import { getAgentDetailApi } from '#/api';

import { fetchAgentDetailByCode } from '../../../ai-agent/agent-resolve';
import {
  defaultAgentConfig,
  normalizeAgentConfig,
  serializeAgentConfig,
} from '../agent-config';
import { FLOW_DIALOG } from '../constants';
import { refreshNodeCard } from '../shapes/registerShapes';
import EmbeddableDialog from './EmbeddableDialog.vue';
import AgentSelector from './selectors/AgentSelector.vue';

defineOptions({ name: 'AgentConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** 确认写回节点后通知外层（NodeConfigPanel → 触发流程保存接口） */
const emit = defineEmits<{ apply: [] }>();

/** AGENT 节点主题色（与 AgentNodeCard 卡片描边一致），驱动小节标题左边条 */
const AGENT_THEME_COLOR = '#722ed1';

const visible = ref(false);
let target: Node | undefined;

/** 配置属性草稿：AGENT 配置（卡片名称直接取所选 Agent 的名称，无独立名称字段） */
const draft = reactive<AgentConfig>(defaultAgentConfig());

/** ElForm 实例：提交时触发校验；校验挂在 draft 上（:model="draft"） */
const formRef = ref<FormInstance>();

/**
 * 校验规则：调用 Agent 必填（未选阻断保存，贴选择器下方红字）。
 * 初始不报错——ElForm 默认懒校验，仅在 validate() / change 后才显示错误。
 */
const rules: FormRules = {
  refAgentCode: [
    { required: true, message: '请选择要调用的 Agent', trigger: 'change' },
  ],
};

/** 用 Object.assign 把归一化后的配置覆盖进 reactive 草稿（保持响应性） */
function applyDraft(cfg: AgentConfig) {
  Object.assign(draft, cfg);
}

/**
 * 回显场景补齐展示名：手里只有 refAgentCode（存量节点/手输值）时，按 code 解析
 * Agent 详情拿到 name，作为卡片标题。解析不出（code 不存在/接口异常）则保留原名不动。
 */
async function resolveRefName(id?: number | string) {
  if (!draft.refAgentCode) return;
  try {
    const detail = await (id === undefined
      ? fetchAgentDetailByCode(draft.refAgentCode)
      : getAgentDetailApi(id));
    if (detail?.name) draft.refName = detail.name;
  } catch {
    // 忽略：解析失败保留已有展示名
  }
}

/**
 * 打开弹窗：读入目标节点当前配置为草稿（缺省字段由 normalize 补全，含旧结构兼容）。
 */
function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  applyDraft(normalizeAgentConfig(data.nodeConfig));
  visible.value = true;
  // 清掉上次遗留的校验态：初始不报错（DOM 就绪后再清）
  nextTick(() => formRef.value?.clearValidate());
  // 回显场景手里只有 refAgentCode，按 code 解析详情补齐展示名
  if (draft.refAgentCode) resolveRefName();
}

/** 被调 Agent 选中时同步展示名（作为卡片标题） */
function onAgentChange(item: AgentOption | undefined) {
  draft.refName = item?.name ?? '';
}

/** 确认：归一化平铺写回 nodeConfig（refAgentCode + 存量 inputMapping/outputMapping）与名称，刷新卡片 */
async function handleConfirm() {
  // 调用 Agent 必填：未选阻断保存（红字贴选择器下方，非弹窗）
  const ok = await formRef.value?.validate().catch(() => false);
  if (ok === false) return;
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const cfg = serializeAgentConfig(draft);
    // 平铺进 nodeConfig（与后端 AgentNodeExecutor 一致），清理可能残留的旧 agent 嵌套结构
    const nodeConfig = { ...data.nodeConfig };
    delete nodeConfig.agent;
    nodeConfig.refAgentCode = cfg.refAgentCode;
    // Input/Output Mapping 不在弹窗编辑，但存量值原样保留写回（不丢旧数据、不改后端契约）
    nodeConfig.inputMapping = cfg.inputMapping;
    nodeConfig.outputMapping = cfg.outputMapping;
    // 展示名仅供回显，用带 __ 前缀的键存放，不参与后端语义
    nodeConfig.__agentRefName = cfg.refName || undefined;
    // 卡片名称直接取所选 Agent 的名称（无独立名称字段）；未选/无名时清空由卡片兜底显示「Agent」
    target.setData(
      { ...data, name: (cfg.refName ?? '').trim(), nodeConfig },
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

defineExpose({ open });
</script>

<template>
  <!-- FLOW_DIALOG：流程编辑器弹窗统一规格；额外 agent-config-dialog 让弹窗可拖拽缩放 -->
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="`${FLOW_DIALOG.class} agent-config-dialog`"
    title="Agent 调用配置"
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
      :style="{ '--type-color': AGENT_THEME_COLOR }"
      @submit.prevent
    >
      <!-- 只配调用 Agent：卡片名称直接取所选 Agent 的名称，无独立名称字段 -->
      <div class="prop-grid-1">
        <ElFormItem label="调用 Agent" prop="refAgentCode">
          <AgentSelector
            v-model="draft.refAgentCode"
            class="w-full"
            @change="onAgentChange"
          />
        </ElFormItem>
      </div>
    </ElForm>

    <template #footer>
      <ElButton v-if="!embedded" @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">
        {{ embedded ? '应用配置' : '保存配置' }}
      </ElButton>
    </template>
  </EmbeddableDialog>
</template>

<style scoped>
/* ===== 呼吸感：容器内边距 + 分组/表单项间距（与 LlmConfigDialog 同款规范） ===== */

/* 整体表单容器充足内边距 */
.prop-form {
  padding: 8px 4px;
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

/* ===== 布局：长项单列（占满整行） ===== */
.prop-grid-1 {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.prop-grid-1 :deep(.el-form-item) {
  min-width: 0;
}

/* ===== 视觉层级：输入框统一高度 40-44px / 圆角 8px / 五态 ===== */

/* 文本输入 / 选择器的外壳统一规格 */
.prop-form :deep(.el-input__wrapper),
.prop-form :deep(.el-select__wrapper) {
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
</style>

<!--
  非 scoped：ElDialog append-to-body 到 <body>，scoped 选择器穿透不到 .el-dialog。
  让 Agent 配置弹窗可由用户拖拽右下角自由缩放（宽 + 高），与 LlmConfigDialog 一致。
-->
<style>
/* 弹窗整体：flex column + resize，用户拖右下角自由缩放（宽 + 高） */
.agent-config-dialog.el-dialog {
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
.agent-config-dialog.el-dialog .el-dialog__header,
.agent-config-dialog.el-dialog .el-dialog__footer {
  flex-shrink: 0;
}

/* 覆盖 flow-prop-dialog 的 max-height:78vh，让 body 随弹窗高度自适应 */
.agent-config-dialog.el-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
}
</style>
