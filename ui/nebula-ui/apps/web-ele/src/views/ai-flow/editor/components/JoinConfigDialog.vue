<script lang="ts" setup>
/**
 * JOIN 汇总节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上 JOIN 节点右键菜单「配置」触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面按节点类型分发 → 本弹窗 open(node)。
 *
 * 配置「并行分支汇聚（fan-in）」策略，分三节：
 *   - 基础：名称
 *   - 汇聚策略：等待模式（全部/任一）+ 失败策略 + 超时
 *   - 输出：输出键 + 合并模式（原样留 context / 收成数组 / 模板拼接）
 * 配置结构与读写归一化集中在 ../join-config（落库到 nodeConfig.join）。
 *
 * 弹窗自持草稿：打开时从节点读入并补全缺省，确认时归一化写回并刷新卡片；
 * 取消丢弃草稿。名称与 join 配置一并写回。
 */
import type { Node } from '@antv/x6';

import type { JoinConfig } from '../join-config';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import { FLOW_DIALOG } from '../constants';
import {
  defaultJoinConfig,
  JOIN_TEMPLATE_PLACEHOLDER,
  normalizeJoinConfig,
  serializeJoinConfig,
} from '../join-config';
import { refreshNodeCard } from '../shapes/registerShapes';
import EmbeddableDialog from './EmbeddableDialog.vue';

defineOptions({ name: 'JoinConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** 确认写回节点后通知外层（NodeConfigPanel → 触发流程保存接口） */
const emit = defineEmits<{ apply: [] }>();

/** JOIN 节点主题色（与 JoinNodeCard 卡片描边一致），驱动小节标题左边条 */
const JOIN_THEME_COLOR = '#ff7875';

/**
 * 模板说明文案。变量示例含双花括号，直接写进模板会被 Vue 编译器当嵌套
 * 插值解析报错，故提到常量里以整段文本插值。
 */
const TEMPLATE_HINT = '用 {{outputKey}} 引用各分支的输出，拼接结果写入输出键';

const visible = ref(false);
let target: Node | undefined;

/** 配置属性草稿：名称 + JOIN 配置 */
const nameDraft = ref('');
const draft = reactive<JoinConfig>(defaultJoinConfig());

/** 用 Object.assign 把归一化后的配置覆盖进 reactive 草稿（保持响应性） */
function applyDraft(cfg: JoinConfig) {
  Object.assign(draft, cfg);
}

/** 打开弹窗：读入目标节点当前配置为草稿（缺省字段由 normalize 补全） */
function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  nameDraft.value = (data.name as string) ?? '';
  applyDraft(normalizeJoinConfig(data.nodeConfig?.join));
  visible.value = true;
}

/** 确认：归一化写回 nodeConfig.join 与名称，刷新卡片 */
function handleConfirm() {
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = {
      ...data.nodeConfig,
      join: serializeJoinConfig(draft),
    };
    target.setData(
      { ...data, name: nameDraft.value.trim(), nodeConfig },
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
  <!-- FLOW_DIALOG：流程编辑器弹窗统一规格；额外 join-config-dialog 让弹窗可拖拽缩放 -->
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="`${FLOW_DIALOG.class} join-config-dialog`"
    title="汇总配置"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-position="top"
      :style="{ '--type-color': JOIN_THEME_COLOR }"
      @submit.prevent
    >
      <!-- 基础：名称 -->
      <div class="prop-grid-1">
        <ElFormItem label="名称">
          <ElInput
            v-model="nameDraft"
            maxlength="64"
            placeholder="卡片标题，缺省显示「汇总」"
          />
        </ElFormItem>
      </div>

      <ElDivider />

      <!-- 汇聚策略：等待模式 + 失败策略 + 超时 -->
      <div class="prop-grid-2">
        <ElFormItem label="等待模式">
          <ElRadioGroup v-model="draft.mode">
            <ElRadioButton value="ALL">全部（ALL）</ElRadioButton>
            <ElRadioButton value="ANY">任一（ANY）</ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="失败策略">
          <ElRadioGroup v-model="draft.onError">
            <ElRadioButton value="FAIL_FAST">快速失败</ElRadioButton>
            <ElRadioButton value="IGNORE">忽略失败分支</ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="超时">
          <ElInputNumber
            v-model="draft.timeoutMs"
            :min="0"
            :step="1000"
            controls-position="right"
            style="width: 100%"
          />
          <span class="hint">毫秒，0 = 不限</span>
        </ElFormItem>
      </div>

      <ElDivider />

      <!-- 输出：合并模式 + 输出键（短项两列）+ 模板（长项单列） -->
      <div class="prop-grid-2">
        <ElFormItem label="合并模式">
          <ElSelect v-model="draft.output.merge" style="width: 100%">
            <ElOption label="不合并（各分支输出原样留在上下文）" value="CONTEXT" />
            <ElOption label="收成数组（按分支顺序）" value="ARRAY" />
            <ElOption label="模板拼接" value="TEMPLATE" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-if="draft.output.merge !== 'CONTEXT'" label="输出键">
          <ElInput
            v-model="draft.output.key"
            maxlength="64"
            placeholder="缺省用节点编码"
          />
        </ElFormItem>
      </div>
      <div v-if="draft.output.merge === 'TEMPLATE'" class="prop-grid-1">
        <ElFormItem label="模板">
          <div class="w-full">
            <div class="tpl-hint">{{ TEMPLATE_HINT }}</div>
            <ElInput
              v-model="draft.output.template"
              :placeholder="JOIN_TEMPLATE_PLACEHOLDER"
              :rows="6"
              class="tpl-area"
              type="textarea"
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
/* ===== 呼吸感：容器内边距 + 分组/表单项间距（与 LlmConfigDialog 同款规范） ===== */

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

/* ===== 视觉层级：输入框统一高度 40-44px / 圆角 8px / 五态 ===== */

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

/* error：红框 */
.prop-form :deep(.el-form-item.is-error .el-input__wrapper),
.prop-form :deep(.el-form-item.is-error .el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

/* success：绿框 */
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

/* 开关/数字框旁的说明文字 */
.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 模板说明小字（贴模板框上方） */
.tpl-hint {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.tpl-area :deep(textarea) {
  font-family: 'JetBrains Mono', consolas, monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>

<!--
  非 scoped：ElDialog append-to-body 到 <body>，scoped 选择器穿透不到 .el-dialog。
  让汇总配置弹窗可由用户拖拽右下角自由缩放（宽 + 高），与 LlmConfigDialog 一致。
-->
<style>
.join-config-dialog.el-dialog {
  display: flex;
  flex-direction: column;
  min-width: 480px;
  max-width: 96vw;
  min-height: 320px;
  max-height: 92vh;
  overflow: hidden;
  resize: both;
}

.join-config-dialog.el-dialog .el-dialog__header,
.join-config-dialog.el-dialog .el-dialog__footer {
  flex-shrink: 0;
}

.join-config-dialog.el-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
}
</style>
