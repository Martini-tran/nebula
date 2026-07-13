<script lang="ts" setup>
/**
 * 开始节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上开始节点右上角的「配置」徽标触发：卡片 → graph.trigger('start:config')
 * → useFlowGraph → 编辑器主页面 → 本弹窗 open(node)。
 *
 * 布局与 PropertyPanel 同规格（820px / top 5vh / flow-prop-dialog 限高滚动），
 * 单面板呈现（不用 tab）：
 *   - 入参：直接编辑 JSON 对象（键为入参标识、值为默认值，写回
 *     nodeConfig.inputs），确认时校验必须是合法 JSON 对象；类型按值推断
 *     （消费侧统一走 normalizeStartInputs）
 *
 * 弹窗自持草稿：打开时从节点读入，确认时校验后写回并刷新卡片；取消丢弃。
 */
import type { Node } from '@antv/x6';

import { ref } from 'vue';

import { ElButton, ElForm, ElFormItem, ElMessage } from 'element-plus';

import { FLOW_DIALOG } from '../constants';
import EmbeddableDialog from './EmbeddableDialog.vue';
import JsonField from './JsonField.vue';
import { refreshNodeCard } from '../shapes/registerShapes';

defineOptions({ name: 'StartConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** 确认写回节点后通知外层（NodeConfigPanel → 触发流程保存接口） */
const emit = defineEmits<{ apply: [] }>();

/** 开始节点主题色（与 StartNodeCard 卡片描边一致），驱动小节标题左边条 */
const START_THEME_COLOR = '#13c2c2';

const visible = ref(false);
let target: Node | undefined;

/** 入参 JSON 草稿 */
const inputsText = ref('');

/** 打开弹窗：读入目标节点当前配置为草稿 */
function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  const inputs = data.nodeConfig?.inputs;
  inputsText.value =
    inputs && typeof inputs === 'object' && Object.keys(inputs).length > 0
      ? JSON.stringify(inputs, null, 2)
      : '';
  visible.value = true;
}

/**
 * 解析并校验入参 JSON。空文本视为无入参；必须是 JSON 对象
 * （键为入参标识、值为默认值），数组或标量均拒绝。
 */
function parseInputs():
  | { inputs: Record<string, any>; ok: true }
  | { message: string; ok: false } {
  const text = inputsText.value.trim();
  if (!text) return { inputs: {}, ok: true };

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { message: '入参不是合法 JSON', ok: false };
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return { message: '入参必须是 JSON 对象（key: value）', ok: false };
  }
  return { inputs: parsed as Record<string, any>, ok: true };
}

/** 确认：校验入参 JSON，通过后写回节点并刷新卡片 */
function handleConfirm() {
  const result = parseInputs();
  if (!result.ok) {
    ElMessage.error(result.message);
    return;
  }
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = { ...data.nodeConfig, inputs: result.inputs };
    target.setData({ ...data, nodeConfig }, { overwrite: true });
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
  <!-- FLOW_DIALOG：流程编辑器弹窗统一规格（body 限高 78vh 滚动） -->
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="FLOW_DIALOG.class"
    title="开始节点配置"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-position="top"
      :style="{ '--type-color': START_THEME_COLOR }"
      @submit.prevent
    >
      <!-- 入参：直接编辑 JSON -->
      <div class="prop-grid-1">
        <ElFormItem label="入参（JSON）">
          <div class="w-full">
            <div class="hint-block">
              JSON 对象：键为入参标识（key），值为默认值，类型按值自动推断
            </div>
            <JsonField
              v-model="inputsText"
              :height="260"
              placeholder="JSON 对象，为空表示无入参"
            />
          </div>
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
/* ===== 呼吸感：容器内边距 + 表单项间距（form-ux-spec 规范） ===== */
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

/* 逻辑分组之间：分割线 + 32px 呼吸间距 */
.prop-form :deep(.el-divider) {
  margin: 32px 0;
}

/* ===== 布局：单列网格 ===== */
.prop-grid-1 {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.prop-grid-1 :deep(.el-form-item) {
  min-width: 0;
}

/* ===== 视觉层级：输入框统一高度 42px / 圆角 8px / 五态 ===== */
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
.prop-form :deep(.el-input.is-disabled .el-input__wrapper) {
  background: var(--el-disabled-bg-color);
  box-shadow: 0 0 0 1px var(--el-disabled-border-color) inset;
  cursor: not-allowed;
}

/* error：红框（提示文案由 el-form-item__error 贴底显示） */
.prop-form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

/* success：绿框 */
.prop-form :deep(.el-form-item.is-success .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-success) inset;
}

/* 错误提示：紧贴输入框下方红色小字（禁用弹窗） */
.prop-form :deep(.el-form-item__error) {
  padding-top: 4px;
  font-size: 12px;
}

/* 占位符：比正文浅 2 级 */
.prop-form :deep(.el-input__inner::placeholder) {
  color: var(--el-text-color-placeholder);
}

/* 字段上方的说明文字 */
.hint-block {
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
