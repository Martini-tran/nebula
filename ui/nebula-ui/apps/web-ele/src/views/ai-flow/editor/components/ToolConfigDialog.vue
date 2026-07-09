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
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { FLOW_DIALOG } from '../constants';
import EmbeddableDialog from './EmbeddableDialog.vue';
import { collectUpstreamVars } from '../composables/useUpstreamVars';
import { refreshNodeCard } from '../shapes/registerShapes';
import {
  defaultToolConfig,
  normalizeToolConfig,
  serializeToolConfig,
  TOOL_DEFAULT_VALUE_PLACEHOLDER,
  TOOL_ERROR_STRATEGIES,
  TOOL_OUTPUT_MODES,
} from '../tool-config';
import InputMappingEditor from './InputMappingEditor.vue';
import ToolSelector from './selectors/ToolSelector.vue';

defineOptions({ name: 'ToolConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

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
  visible.value = false;
}

function handleClose() {
  visible.value = false;
}

/** 格式化默认值 JSON（非法时提示，不改动原文） */
function formatDefaultValue() {
  const text = (draft.error.defaultValue ?? '').trim();
  if (!text) return;
  try {
    draft.error.defaultValue = JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    ElMessage.warning('默认值不是合法 JSON，无法格式化');
  }
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
  <!-- FLOW_DIALOG：流程编辑器弹窗统一规格（body 限高 78vh 滚动） -->
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="FLOW_DIALOG.class"
    :title="dialogTitle"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      ref="formRef"
      class="prop-form"
      label-width="128px"
      :style="{ '--type-color': TOOL_THEME_COLOR }"
      @submit.prevent
    >
      <!-- 工具配置：名称 + Tool + Parameters（embedded 时三段全展示，data-section 供滚动定位） -->
      <template v-if="embedded || section === 'tool'">
        <section class="prop-section" data-section="tool">
          <div class="prop-section-title">基础</div>
          <div class="prop-grid">
            <ElFormItem label="名称">
              <ElInput
                v-model="nameDraft"
                maxlength="64"
                placeholder="卡片标题，缺省显示「工具」"
              />
            </ElFormItem>
          </div>
        </section>

        <section class="prop-section">
          <div class="prop-section-title">工具</div>
          <div class="prop-grid">
            <ElFormItem label="工具">
              <ToolSelector
                v-model="draft.tool.toolCode"
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
        </section>

        <section class="prop-section">
          <div class="prop-section-title">调用参数</div>
          <div class="prop-grid">
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
        </section>
      </template>

      <!-- 输入输出配置：Input + Output -->
      <template v-if="embedded || section === 'io'">
        <section class="prop-section" data-section="io">
          <div class="prop-section-title">输入</div>
          <div class="prop-grid">
            <ElFormItem class="span-2" label="入参映射">
              <InputMappingEditor
                v-model="draft.input.mapping"
                key-placeholder="工具入参名"
                value-placeholder="上游变量/上下文键"
                :options="upstreamVars"
              />
            </ElFormItem>
          </div>
        </section>

        <section class="prop-section">
          <div class="prop-section-title">输出</div>
          <div class="prop-grid">
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
            <ElFormItem
              v-if="draft.output.mode === 'FIELD'"
              class="span-2"
              label="字段映射"
            >
              <InputMappingEditor
                v-model="draft.output.mapping"
                key-placeholder="返回字段"
                value-placeholder="流程变量"
              />
            </ElFormItem>
          </div>
        </section>
      </template>

      <!-- 异常处理配置：Error 策略 -->
      <template v-if="embedded || section === 'error'">
        <section class="prop-section" data-section="error">
          <div class="prop-section-title">异常处理</div>
          <div class="prop-grid">
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
            <ElFormItem
              v-if="draft.error.strategy === 'DEFAULT'"
              class="span-2"
              label-width="0"
            >
              <div class="w-full">
                <div class="mb-2 flex items-center justify-between">
                  <span class="text-xs text-[var(--el-text-color-secondary)]">
                    工具失败时返回的默认值（JSON，可选）
                  </span>
                  <ElButton link size="small" @click="formatDefaultValue">
                    格式化
                  </ElButton>
                </div>
                <ElInput
                  v-model="draft.error.defaultValue"
                  :placeholder="TOOL_DEFAULT_VALUE_PLACEHOLDER"
                  :rows="8"
                  class="json-area"
                  type="textarea"
                />
              </div>
            </ElFormItem>
          </div>
        </section>
      </template>
    </ElForm>

    <template #footer>
      <ElButton v-if="!embedded" @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">
        {{ embedded ? '应用' : '确定' }}
      </ElButton>
    </template>
  </EmbeddableDialog>
</template>

<style scoped>
.prop-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

/* 属性分类小节：与 LlmConfigDialog / PropertyPanel 同款 */
.prop-section {
  padding: 4px 0 2px;
}

.prop-section + .prop-section {
  margin-top: 4px;
}

.prop-section-title {
  padding-left: 8px;
  margin: 6px 0 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  border-left: 3px solid var(--type-color, var(--el-color-primary));
}

/* 两列网格：与 PropertyPanel 同款；.span-2 的项占满整行 */
.prop-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.prop-grid :deep(.el-form-item) {
  min-width: 0;
  margin-bottom: 12px;
}

.prop-grid :deep(.span-2) {
  grid-column: 1 / -1;
}

/* 开关旁的说明文字 */
.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.json-area :deep(textarea) {
  font-family: 'JetBrains Mono', consolas, monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
