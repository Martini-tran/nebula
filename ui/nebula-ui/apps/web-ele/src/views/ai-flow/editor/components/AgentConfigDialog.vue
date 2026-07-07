<script lang="ts" setup>
/**
 * AGENT 节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上 AGENT 节点右键菜单「配置」触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面按节点类型分发 → 本弹窗 open(node)。
 *
 * AGENT 节点「调用另一个已设计好的 Agent（复用 Workflow）」，递归执行子 Agent，配置三块：
 *   - 调用 Agent：选被调子 Agent 的 agentCode（Agent 定义）
 *   - Input Mapping：子入参键 ← 父上下文键
 *   - Output Mapping：父上下文键 ← 子产物键
 * 配置结构与读写归一化集中在 ../agent-config（平铺落库到 nodeConfig.refAgentCode/inputMapping/outputMapping），
 * 与后端 AgentNodeExecutor 严格一致。
 *
 * 弹窗自持草稿：打开时从节点读入并补全缺省（含旧结构兼容），确认时归一化平铺写回并刷新卡片；取消丢弃草稿。
 */
import type { Node } from '@antv/x6';

import type { AgentConfig } from '../agent-config';
import type { StartInputParam } from '../start-input';
import type { AgentOption } from './selectors/AgentSelector.vue';

import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

import { getAgentDetailApi } from '#/api';

import { fetchAgentDetailByCode } from '../../../ai-agent/agent-resolve';
import { schemaToStartInputs } from '../../../ai-agent/schema-form';
import {
  defaultAgentConfig,
  normalizeAgentConfig,
  serializeAgentConfig,
} from '../agent-config';
import { FLOW_DIALOG } from '../constants';
import EmbeddableDialog from './EmbeddableDialog.vue';
import { refreshNodeCard } from '../shapes/registerShapes';
import InputMappingEditor from './InputMappingEditor.vue';
import SchemaMappingEditor from './SchemaMappingEditor.vue';
import AgentSelector from './selectors/AgentSelector.vue';

defineOptions({ name: 'AgentConfigDialog' });

/** embedded：内嵌到 NodeConfigDrawer 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** AGENT 节点主题色（与 AgentNodeCard 卡片描边一致），驱动小节标题左边条 */
const AGENT_THEME_COLOR = '#722ed1';

const visible = ref(false);
let target: Node | undefined;

/** 配置属性草稿：名称 + AGENT 配置 */
const nameDraft = ref('');
const draft = reactive<AgentConfig>(defaultAgentConfig());

/** 用 Object.assign 把归一化后的配置覆盖进 reactive 草稿（保持响应性） */
function applyDraft(cfg: AgentConfig) {
  Object.assign(draft, cfg);
}

// ---------------- 目标 Agent 的 IO 契约（schema 驱动映射行） ----------------
const inputFields = ref<StartInputParam[]>([]);
const outputFields = ref<StartInputParam[]>([]);
const contractLoading = ref(false);

/** 拉目标 Agent 详情并解析 IO 契约；解析不出（无 schema/手输 code）退回自由填写 */
async function loadContract(id?: number | string) {
  if (!draft.refAgentCode) {
    inputFields.value = [];
    outputFields.value = [];
    return;
  }
  contractLoading.value = true;
  try {
    const detail = await (id === undefined
      ? fetchAgentDetailByCode(draft.refAgentCode)
      : getAgentDetailApi(id));
    inputFields.value = schemaToStartInputs(detail?.inputSchema);
    outputFields.value = schemaToStartInputs(detail?.outputSchema);
  } catch {
    inputFields.value = [];
    outputFields.value = [];
  } finally {
    contractLoading.value = false;
  }
}

/** schema 字段之外的存量 Input 映射（归入「自定义补充」编辑，旧数据不丢） */
const inputExtras = computed<Record<string, string>>({
  get: () => {
    const known = new Set(inputFields.value.map((f) => f.key));
    return Object.fromEntries(
      Object.entries(draft.inputMapping).filter(([k]) => !known.has(k)),
    );
  },
  set: (extras) => {
    const known = new Set(inputFields.value.map((f) => f.key));
    const schemaPart = Object.fromEntries(
      Object.entries(draft.inputMapping).filter(([k]) => known.has(k)),
    );
    draft.inputMapping = { ...schemaPart, ...extras };
  },
});

/** schema 产物键之外的存量 Output 映射（value=子产物键 不在契约里的条目） */
const outputExtras = computed<Record<string, string>>({
  get: () => {
    const known = new Set(outputFields.value.map((f) => f.key));
    return Object.fromEntries(
      Object.entries(draft.outputMapping).filter(([, v]) => !known.has(v)),
    );
  },
  set: (extras) => {
    const known = new Set(outputFields.value.map((f) => f.key));
    const schemaPart = Object.fromEntries(
      Object.entries(draft.outputMapping).filter(([, v]) => known.has(v)),
    );
    draft.outputMapping = { ...schemaPart, ...extras };
  },
});

/**
 * 打开弹窗：读入目标节点当前配置为草稿（缺省字段由 normalize 补全，含旧结构兼容）。
 */
function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  nameDraft.value = (data.name as string) ?? '';
  applyDraft(normalizeAgentConfig(data.nodeConfig));
  inputFields.value = [];
  outputFields.value = [];
  visible.value = true;
  // 回显场景手里只有 refAgentCode，按 code 解析详情拉契约
  if (draft.refAgentCode) loadContract();
}

/** 被调 Agent 选中时同步展示名并按其契约重建映射行 */
function onAgentChange(item: AgentOption | undefined) {
  draft.refName = item?.name ?? '';
  loadContract(item?.id);
}

/** 确认：归一化平铺写回 nodeConfig（refAgentCode/inputMapping/outputMapping）与名称，刷新卡片 */
function handleConfirm() {
  // 契约必填校验：input_schema required 字段必须已映射
  const missing = inputFields.value.filter(
    (f) => f.required && f.key && !draft.inputMapping[f.key],
  );
  if (missing.length > 0) {
    ElMessage.warning(
      `请为必填入参配置映射：${missing.map((f) => f.key).join('、')}`,
    );
    return;
  }
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const cfg = serializeAgentConfig(draft);
    // 平铺进 nodeConfig（与后端 AgentNodeExecutor 一致），清理可能残留的旧 agent 嵌套结构
    const nodeConfig = { ...data.nodeConfig };
    delete nodeConfig.agent;
    nodeConfig.refAgentCode = cfg.refAgentCode;
    nodeConfig.inputMapping = cfg.inputMapping;
    nodeConfig.outputMapping = cfg.outputMapping;
    // 展示名仅供回显，用带 __ 前缀的键存放，不参与后端语义
    nodeConfig.__agentRefName = cfg.refName || undefined;
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
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="FLOW_DIALOG.class"
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
        <div class="prop-section-title">调用 Agent</div>
        <div class="prop-grid">
          <ElFormItem class="span-2" label="调用 Agent">
            <AgentSelector
              v-model="draft.refAgentCode"
              @change="onAgentChange"
            />
          </ElFormItem>
        </div>
      </section>

      <section class="prop-section">
        <div class="prop-section-title">Input Mapping</div>
        <div class="prop-grid">
          <ElFormItem class="span-2" label-width="0">
            <div v-loading="contractLoading" class="w-full">
              <div class="mb-2 text-xs text-[var(--el-text-color-secondary)]">
                子 Agent 入参 ← 父流程上下文键（左：子入参键，右：父上下文键）
              </div>
              <!-- 契约驱动：入参键由目标 Agent 的 input_schema 锁定，只填父上下文键 -->
              <SchemaMappingEditor
                v-if="inputFields.length > 0"
                v-model="draft.inputMapping"
                :fields="inputFields"
                editable-placeholder="父上下文键"
                fixed-side="key"
              />
              <template v-if="inputFields.length > 0">
                <div
                  v-if="Object.keys(inputExtras).length > 0"
                  class="mt-3"
                >
                  <div class="mb-2 text-xs text-[var(--el-text-color-secondary)]">
                    自定义补充（契约之外的存量映射）
                  </div>
                  <InputMappingEditor
                    v-model="inputExtras"
                    key-placeholder="子入参键"
                    value-placeholder="父上下文键"
                  />
                </div>
              </template>
              <!-- 契约缺失/解析失败：退回自由填写（零回归） -->
              <InputMappingEditor
                v-else
                v-model="draft.inputMapping"
                key-placeholder="子入参键"
                value-placeholder="父上下文键"
              />
            </div>
          </ElFormItem>
        </div>
      </section>

      <section class="prop-section">
        <div class="prop-section-title">Output Mapping</div>
        <div class="prop-grid">
          <ElFormItem class="span-2" label-width="0">
            <div v-loading="contractLoading" class="w-full">
              <div class="mb-2 text-xs text-[var(--el-text-color-secondary)]">
                父流程上下文键 ← 子 Agent 产物键（左：父上下文键，右：子产物键）
              </div>
              <!-- 契约驱动：产物键由目标 Agent 的 output_schema 锁定，只填父上下文键 -->
              <SchemaMappingEditor
                v-if="outputFields.length > 0"
                v-model="draft.outputMapping"
                :fields="outputFields"
                editable-placeholder="父上下文键"
                fixed-side="value"
              />
              <template v-if="outputFields.length > 0">
                <div
                  v-if="Object.keys(outputExtras).length > 0"
                  class="mt-3"
                >
                  <div class="mb-2 text-xs text-[var(--el-text-color-secondary)]">
                    自定义补充（契约之外的存量映射）
                  </div>
                  <InputMappingEditor
                    v-model="outputExtras"
                    key-placeholder="父上下文键"
                    value-placeholder="子产物键"
                  />
                </div>
              </template>
              <InputMappingEditor
                v-else
                v-model="draft.outputMapping"
                key-placeholder="父上下文键"
                value-placeholder="子产物键"
              />
            </div>
          </ElFormItem>
        </div>
      </section>
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
</style>
