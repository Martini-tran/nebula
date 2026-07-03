<script lang="ts" setup>
/**
 * AGENT 节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上 AGENT 节点右键菜单「配置」触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面按节点类型分发 → 本弹窗 open(node)。
 *
 * AGENT 节点「调用另一个已设计好的 Agent（复用 Workflow）」，配置两块：
 *   - Agent：选被调 Agent（已保存流程的 flowCode）
 *   - 调用参数：JSON 文本，传给被调 Agent 的入参
 * 配置结构与读写归一化集中在 ../agent-config（落库到 nodeConfig.agent）。
 *
 * 弹窗自持草稿：打开时从节点读入并补全缺省，确认时归一化写回并刷新卡片；
 * 取消丢弃草稿。名称与 agent 配置一并写回。
 */
import type { Node } from '@antv/x6';

import type { AgentConfig } from '../agent-config';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

import {
  AGENT_PARAMS_PLACEHOLDER,
  defaultAgentConfig,
  normalizeAgentConfig,
  serializeAgentConfig,
} from '../agent-config';
import { FLOW_DIALOG } from '../constants';
import { refreshNodeCard } from '../shapes/registerShapes';
import AgentSelector from './selectors/AgentSelector.vue';

defineOptions({ name: 'AgentConfigDialog' });

/** AGENT 节点主题色（与 AgentNodeCard 卡片描边一致），驱动小节标题左边条 */
const AGENT_THEME_COLOR = '#722ed1';

/**
 * 调用参数说明文案。变量示例含双花括号，若直接写进模板 mustache 会被 Vue
 * 编译器当嵌套插值解析报错，故提到常量里以整段文本插值。
 */
const PARAMS_HINT = '传给被调 Agent 的入参（JSON，可选），支持 {{inputs.xxx}} 变量';

const visible = ref(false);
let target: Node | undefined;

/** 配置属性草稿：名称 + AGENT 配置 */
const nameDraft = ref('');
const draft = reactive<AgentConfig>(defaultAgentConfig());

/** 用 Object.assign 把归一化后的配置覆盖进 reactive 草稿（保持响应性） */
function applyDraft(cfg: AgentConfig) {
  Object.assign(draft, cfg);
}

/**
 * 打开弹窗：读入目标节点当前配置为草稿（缺省字段由 normalize 补全）。
 */
function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  nameDraft.value = (data.name as string) ?? '';
  applyDraft(normalizeAgentConfig(data.nodeConfig?.agent));
  visible.value = true;
}

/** 被调 Agent 选中时同步展示名（回显用） */
function onAgentChange(item: { flowCode: string; name?: string } | undefined) {
  draft.ref.name = item?.name ?? '';
}

/** 校验调用参数 JSON（空视为合法）；非法时提示并阻断确认 */
function validateParamsJson(): boolean {
  const text = (draft.params.json ?? '').trim();
  if (!text) return true;
  try {
    JSON.parse(text);
    return true;
  } catch {
    ElMessage.error('调用参数不是合法 JSON');
    return false;
  }
}

/** 格式化调用参数 JSON（非法时提示，不改动原文） */
function formatParams() {
  const text = (draft.params.json ?? '').trim();
  if (!text) return;
  try {
    draft.params.json = JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    ElMessage.warning('调用参数不是合法 JSON，无法格式化');
  }
}

/** 确认：校验通过后归一化写回 nodeConfig.agent 与名称，刷新卡片 */
function handleConfirm() {
  if (!validateParamsJson()) return;
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = {
      ...data.nodeConfig,
      agent: serializeAgentConfig(draft),
    };
    target.setData(
      { ...data, name: nameDraft.value.trim(), nodeConfig },
      { overwrite: true },
    );
    refreshNodeCard(target);
  }
  visible.value = false;
}

function handleClose() {
  visible.value = false;
}

defineExpose({ open });
</script>

<template>
  <!-- FLOW_DIALOG：流程编辑器弹窗统一规格（body 限高 78vh 滚动） -->
  <ElDialog
    v-model="visible"
    append-to-body
    :class="FLOW_DIALOG.class"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    destroy-on-close
    title="Agent 调用配置"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-width="128px"
      :style="{ '--type-color': AGENT_THEME_COLOR }"
      @submit.prevent
    >
      <section class="prop-section">
        <div class="prop-section-title">基础</div>
        <div class="prop-grid">
          <ElFormItem label="名称">
            <ElInput
              v-model="nameDraft"
              maxlength="64"
              placeholder="卡片标题，缺省显示「Agent」"
            />
          </ElFormItem>
        </div>
      </section>

      <section class="prop-section">
        <div class="prop-section-title">Agent</div>
        <div class="prop-grid">
          <ElFormItem class="span-2" label="调用 Agent">
            <AgentSelector
              v-model="draft.ref.flowCode"
              @change="onAgentChange"
            />
          </ElFormItem>
        </div>
      </section>

      <section class="prop-section">
        <div class="prop-section-title">调用参数</div>
        <div class="prop-grid">
          <ElFormItem class="span-2" label-width="0">
            <div class="w-full">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-[var(--el-text-color-secondary)]">
                  {{ PARAMS_HINT }}
                </span>
                <ElButton link size="small" @click="formatParams">
                  格式化
                </ElButton>
              </div>
              <ElInput
                v-model="draft.params.json"
                :placeholder="AGENT_PARAMS_PLACEHOLDER"
                :rows="12"
                class="json-area"
                type="textarea"
              />
            </div>
          </ElFormItem>
        </div>
      </section>
    </ElForm>

    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">确定</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.prop-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

/* 属性分类小节：与 LlmConfigDialog / ToolConfigDialog 同款 */
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

.json-area :deep(textarea) {
  font-family: 'JetBrains Mono', consolas, monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
