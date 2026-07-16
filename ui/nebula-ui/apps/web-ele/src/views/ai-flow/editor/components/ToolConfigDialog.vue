<script lang="ts" setup>
/**
 * TOOL 节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上 TOOL 节点右键菜单触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面按节点类型分发 → 本弹窗 open(node, section)。
 *
 * 右键菜单按模块拆成三项（工具/输入输出/异常），每项打开本弹窗并只渲染
 * 对应 section（标题随之变化），避免单个大 Tab 弹窗。三个 section：
 *   tool（名称 + Tool + Parameters）/ io（Input + Output）/ error（Error 策略）
 * 配置结构与读写归一化集中在 ../tool-config（落库到 nodeConfig.tool）。
 *
 * 弹窗自持草稿：打开时从节点读入并补全缺省（含旧散字段回迁），确认时归一化
 * 写回并刷新卡片；取消丢弃草稿。名称与 tool 配置一并写回（无论打开哪个 section）。
 */
import type { Node } from '@antv/x6';

import type { ToolConfig } from '../tool-config';

import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { collectUpstreamVars } from '../composables/useUpstreamVars';
import { FLOW_DIALOG } from '../constants';
import { refreshNodeCard } from '../shapes/registerShapes';
import {
  defaultToolConfig,
  normalizeToolConfig,
  serializeToolConfig,
  TOOL_DEFAULT_VALUE_PLACEHOLDER,
  TOOL_ERROR_STRATEGIES,
  TOOL_OUTPUT_MODES,
} from '../tool-config';
import EmbeddableDialog from './EmbeddableDialog.vue';
import InputMappingEditor from './InputMappingEditor.vue';
import JsonField from './JsonField.vue';
import ToolSelector from './selectors/ToolSelector.vue';

defineOptions({ name: 'ToolConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** 确认写回节点后通知外层（NodeConfigPanel → 触发流程保存接口） */
const emit = defineEmits<{ apply: [] }>();

/** TOOL 节点主题色（与 ToolNodeCard 卡片描边一致），驱动小节标题左边条 */
const TOOL_THEME_COLOR = '#13c2c2';

/**
 * 配置分区（对应右键菜单项）：
 * - tool：名称 + Tool 选择 + Parameters 调用参数
 * - io：Input 入参映射 + Output 输出映射
 * - error：Error 失败策略
 */
type ToolSection = 'error' | 'io' | 'tool';

/** 分区标题（弹窗 title 随打开的 section 变化） */
const SECTION_TITLES: Record<ToolSection, string> = {
  tool: '工具配置',
  io: '输入输出配置',
  error: '异常处理配置',
};

const visible = ref(false);
const section = ref<ToolSection>('tool');
const dialogTitle = computed(() => SECTION_TITLES[section.value]);
let target: Node | undefined;

/** 配置属性草稿：名称 + TOOL 五大模块配置 */
const nameDraft = ref('');
const draft = reactive<ToolConfig>(defaultToolConfig());

/** 上游可用变量候选（供入参映射下拉，open 时按当前节点收集） */
const upstreamVars = ref<ReturnType<typeof collectUpstreamVars>>([]);

/** 用 Object.assign 把归一化后的配置覆盖进 reactive 草稿（保持响应性） */
function applyDraft(cfg: ToolConfig) {
  Object.assign(draft, cfg);
}

/**
 * 打开弹窗：读入目标节点当前配置为草稿（缺省字段由 normalize 补全，
 * 旧 ToolNodeForm 散字段一并回迁），并按 section 决定渲染哪一模块。
 * 缺省打开「工具配置」。
 */
function open(node: Node, target_section: ToolSection = 'tool') {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  nameDraft.value = (data.name as string) ?? '';
  applyDraft(normalizeToolConfig(data.nodeConfig?.tool, data));
  // 收集上游可用变量（供入参映射下拉）
  upstreamVars.value = collectUpstreamVars(node.model?.graph, node);
  section.value = target_section;
  visible.value = true;
}

/** 校验默认值 JSON（仅 DEFAULT 策略且非空时校验） */
function validateDefaultValue(): boolean {
  if (draft.error.strategy !== 'DEFAULT') return true;
  const text = (draft.error.defaultValue ?? '').trim();
  if (!text) return true;
  try {
    JSON.parse(text);
    return true;
  } catch {
    ElMessage.error('默认值不是合法 JSON');
    return false;
  }
}

/** 确认：校验通过后归一化写回 nodeConfig.tool 与名称，刷新卡片 */
function handleConfirm() {
  if (!validateDefaultValue()) return;
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = {
      ...data.nodeConfig,
      tool: serializeToolConfig(draft),
    };
    // 清理旧 ToolNodeForm 落的散字段，避免与新结构并存造成回显歧义
    delete nodeConfig.toolCode;
    const next: Record<string, any> = {
      ...data,
      name: nameDraft.value.trim(),
      nodeConfig,
    };
    delete next.inputMapping;
    target.setData(next, { overwrite: true });
    refreshNodeCard(target);
  }
  emit('apply');
  visible.value = false;
}

function handleClose() {
  visible.value = false;
}


/** ElForm 组件实例：取 $el 拿到原生根元素，用于向上找滚动容器 */
const formRef = ref<{ $el?: HTMLElement }>();

/**
 * 滚动定位到指定小节。embedded 模式下三段全展示，右键菜单靠本方法定位，
 * 取代原「只渲染一段」的弹窗行为。与 open 职责分离：open 只 hydrate，本方法只滚动。
 * 滚动容器是 EmbeddableDialog 内嵌形态的 .embedded-form（overflow-y:auto 那层）。
 */
function focusSection(target: ToolSection) {
  formRef.value?.$el
    ?.closest('.embedded-form')
    ?.querySelector(`[data-section="${target}"]`)
    ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

defineExpose({ focusSection, open });
</script>

<template>
  <!-- FLOW_DIALOG：流程编辑器弹窗统一规格；额外 tool-config-dialog 让弹窗可拖拽缩放 -->
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="`${FLOW_DIALOG.class} tool-config-dialog`"
    :title="dialogTitle"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      ref="formRef"
      class="prop-form"
      label-position="top"
      :style="{ '--type-color': TOOL_THEME_COLOR }"
      @submit.prevent
    >
      <!-- 工具配置：名称 + Tool + Parameters（embedded 时三段全展示，data-section 供滚动定位） -->
      <template v-if="embedded || section === 'tool'">
        <div data-section="tool">
          <!-- 基础：名称 -->
          <div class="prop-grid-1">
            <ElFormItem label="名称">
              <ElInput
                v-model="nameDraft"
                maxlength="64"
                placeholder="卡片标题，缺省显示「工具」"
              />
            </ElFormItem>
          </div>

          <ElDivider />

          <!-- 工具：工具 + 版本 -->
          <div class="prop-grid-2">
            <ElFormItem label="工具">
              <ToolSelector
                v-model="draft.tool.toolCode"
                class="w-full"
                placeholder="选择要调用的工具"
              />
            </ElFormItem>
            <ElFormItem label="版本">
              <ElInput
                v-model="draft.tool.version"
                placeholder="工具版本（可选）"
              />
            </ElFormItem>
          </div>

          <ElDivider />

          <!-- 调用参数：超时 + 重试 + 忽略错误 + 异步 -->
          <div class="prop-grid-2">
            <ElFormItem label="超时（ms）">
              <ElInputNumber
                v-model="draft.parameters.timeout"
                :min="0"
                :step="500"
                controls-position="right"
                placeholder="可选"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem label="重试次数">
              <ElInputNumber
                v-model="draft.parameters.retry"
                :min="0"
                controls-position="right"
                placeholder="可选"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem label="忽略错误">
              <ElSwitch v-model="draft.parameters.ignoreError" />
              <span class="hint">失败不中断流程</span>
            </ElFormItem>
            <ElFormItem label="异步执行">
              <ElSwitch v-model="draft.parameters.async" />
              <span class="hint">不等待返回</span>
            </ElFormItem>
          </div>
        </div>
      </template>

      <!-- 输入输出配置：Input + Output -->
      <template v-if="embedded || section === 'io'">
        <!-- embedded 三段全展示时，与上一段之间补分割线 -->
        <ElDivider v-if="embedded" />
        <div data-section="io">
          <!-- 输入：入参映射（长项单列） -->
          <div class="prop-grid-1">
            <ElFormItem label="入参映射">
              <InputMappingEditor
                v-model="draft.input.mapping"
                key-placeholder="工具入参名"
                value-placeholder="上游变量/上下文键"
                :options="upstreamVars"
              />
            </ElFormItem>
          </div>

          <ElDivider />

          <!-- 输出：输出模式 + 输出键（短项两列）+ 字段映射（长项单列） -->
          <div class="prop-grid-2">
            <ElFormItem label="输出模式">
              <ElSelect v-model="draft.output.mode" style="width: 100%">
                <ElOption
                  v-for="opt in TOOL_OUTPUT_MODES"
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
          <div v-if="draft.output.mode === 'FIELD'" class="prop-grid-1">
            <ElFormItem label="字段映射">
              <InputMappingEditor
                v-model="draft.output.mapping"
                key-placeholder="返回字段"
                value-placeholder="流程变量"
              />
            </ElFormItem>
          </div>
        </div>
      </template>

      <!-- 异常处理配置：Error 策略 -->
      <template v-if="embedded || section === 'error'">
        <!-- embedded 三段全展示时，与上一段之间补分割线 -->
        <ElDivider v-if="embedded" />
        <div data-section="error">
          <!-- 失败策略 + 默认值 JSON -->
          <div class="prop-grid-2">
            <ElFormItem label="失败策略">
              <ElSelect v-model="draft.error.strategy" style="width: 100%">
                <ElOption
                  v-for="opt in TOOL_ERROR_STRATEGIES"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
          </div>
          <div v-if="draft.error.strategy === 'DEFAULT'" class="prop-grid-1">
            <ElFormItem label="默认值（JSON，可选）">
              <div class="w-full">
                <div class="json-hint">工具失败时返回的默认值</div>
                <JsonField
                  v-model="draft.error.defaultValue"
                  :height="200"
                  :placeholder="TOOL_DEFAULT_VALUE_PLACEHOLDER"
                />
              </div>
            </ElFormItem>
          </div>
        </div>
      </template>
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

/* JSON 默认值说明小字（贴 JsonField 上方） */
.json-hint {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
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

/* 开关旁的说明文字 */
.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

<!--
  非 scoped：ElDialog append-to-body 到 <body>，scoped 选择器穿透不到 .el-dialog。
  让工具配置弹窗可由用户拖拽右下角自由缩放（宽 + 高），与 LlmConfigDialog 一致。
-->
<style>
.tool-config-dialog.el-dialog {
  display: flex;
  flex-direction: column;
  min-width: 480px;
  max-width: 96vw;
  min-height: 320px;
  max-height: 92vh;
  overflow: hidden;
  resize: both;
}

.tool-config-dialog.el-dialog .el-dialog__header,
.tool-config-dialog.el-dialog .el-dialog__footer {
  flex-shrink: 0;
}

.tool-config-dialog.el-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
}
</style>
