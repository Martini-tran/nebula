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

import type { AiModelProfileApi } from '#/api';

import type { LlmConfig } from '../llm-config';

import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { FLOW_DIALOG } from '../constants';
import {
  defaultLlmConfig,
  LLM_ADVANCED_PLACEHOLDER,
  LLM_OUTPUT_TYPES,
  LLM_PROVIDERS,
  normalizeLlmConfig,
  serializeLlmConfig,
} from '../llm-config';
import { refreshNodeCard } from '../shapes/registerShapes';
import InputMappingEditor from './InputMappingEditor.vue';
import ProfileSelector from './selectors/ProfileSelector.vue';

defineOptions({ name: 'LlmConfigDialog' });

/** LLM 节点主题色（与 LlmNodeCard 卡片描边一致），驱动小节标题左边条 */
const LLM_THEME_COLOR = '#13c2c2';

/**
 * 配置分区（对应右键菜单项）：
 * - basic：名称 + 上下文
 * - model：模型 + 调用参数 + 输出
 * - prompt：System / User Prompt + 变量
 */
type LlmSection = 'basic' | 'model' | 'prompt';

/** 分区标题（弹窗 title 随打开的 section 变化） */
const SECTION_TITLES: Record<LlmSection, string> = {
  basic: '基础配置',
  model: '模型配置',
  prompt: '提示词配置',
};

const visible = ref(false);
const section = ref<LlmSection>('basic');
const dialogTitle = computed(() => SECTION_TITLES[section.value]);
let target: Node | undefined;

/** 配置属性草稿：名称 + LLM 五大模块配置 */
const nameDraft = ref('');
const draft = reactive<LlmConfig>(defaultLlmConfig());

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
  nameDraft.value = (data.name as string) ?? '';
  applyDraft(normalizeLlmConfig(data.nodeConfig?.llm));
  section.value = target_section;
  visible.value = true;
}

/**
 * 选中模型档案：把档案的 provider/model/baseUrl 与基础参数
 * （temperature/topP/maxTokens）带出填入草稿作为默认值。
 * 带出后用户仍可手动覆盖下方任一字段；清空档案则保留当前手填值不动。
 * 密钥不带出（档案下行只有掩码），需要时用户在「密钥」自行填写。
 * 仅覆盖档案中确有值的字段，避免用空值冲掉用户已填内容。
 */
function onProfileChange(item: AiModelProfileApi.ProfileItem | undefined) {
  if (!item) return; // 清空档案：只清 profileCode（由 v-model 完成），字段保留
  if (item.provider) draft.model.provider = item.provider;
  if (item.model) draft.model.model = item.model;
  if (item.baseUrl) draft.model.baseUrl = item.baseUrl;
  if (item.temperature != null) {
    draft.parameters.basic.temperature = item.temperature;
  }
  if (item.topP != null) draft.parameters.basic.topP = item.topP;
  if (item.maxTokens != null) draft.parameters.basic.maxTokens = item.maxTokens;
}

/** 校验高级参数 JSON（空视为合法）；非法时提示并阻断确认 */
function validateAdvancedJson(): boolean {
  if (draft.parameters.mode !== 'advanced') return true;
  const text = draft.parameters.advanced.trim();
  if (!text) return true;
  try {
    JSON.parse(text);
    return true;
  } catch {
    ElMessage.error('高级参数不是合法 JSON');
    return false;
  }
}

/** 校验输出 JSON Schema（仅 JSON 输出且非空时校验） */
function validateJsonSchema(): boolean {
  if (draft.output.type !== 'JSON') return true;
  const text = (draft.output.jsonSchema ?? '').trim();
  if (!text) return true;
  try {
    JSON.parse(text);
    return true;
  } catch {
    ElMessage.error('JSON Schema 不是合法 JSON');
    return false;
  }
}

/** 确认：校验通过后归一化写回 nodeConfig.llm 与名称，刷新卡片 */
function handleConfirm() {
  if (!validateAdvancedJson() || !validateJsonSchema()) return;
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = {
      ...data.nodeConfig,
      llm: serializeLlmConfig(draft),
    };
    target.setData(
      {
        ...data,
        name: nameDraft.value.trim(),
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

/** 格式化高级参数 JSON（非法时提示，不改动原文） */
function formatAdvanced() {
  const text = draft.parameters.advanced.trim();
  if (!text) return;
  try {
    draft.parameters.advanced = JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    ElMessage.warning('高级参数不是合法 JSON，无法格式化');
  }
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
    :title="dialogTitle"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-width="128px"
      :style="{ '--type-color': LLM_THEME_COLOR }"
      @submit.prevent
    >
      <!-- 基础配置：名称 + 上下文 -->
      <template v-if="section === 'basic'">
        <section class="prop-section">
          <div class="prop-section-title">基础</div>
          <div class="prop-grid">
            <ElFormItem label="名称">
              <ElInput
                v-model="nameDraft"
                maxlength="64"
                placeholder="卡片标题，缺省显示「LLM」"
              />
            </ElFormItem>
          </div>
        </section>

        <section class="prop-section">
          <div class="prop-section-title">上下文</div>
          <div class="prop-grid">
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
        </section>
      </template>

      <!-- 模型配置：Model + 调用参数 -->
      <template v-else-if="section === 'model'">
        <section class="prop-section">
          <div class="prop-section-title">模型</div>
          <div class="prop-grid">
            <ElFormItem class="span-2" label="模型档案">
              <ProfileSelector
                v-model="draft.model.profileCode"
                class="w-full"
                placeholder="选择档案自动带出模型信息（可选，可手动覆盖）"
                @change="onProfileChange"
              />
            </ElFormItem>
            <ElFormItem label="提供商">
              <ElSelect
                v-model="draft.model.provider"
                allow-create
                clearable
                default-first-option
                filterable
                placeholder="模型提供商"
                style="width: 100%"
              >
                <ElOption
                  v-for="p in LLM_PROVIDERS"
                  :key="p"
                  :label="p"
                  :value="p"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="模型">
              <ElInput
                v-model="draft.model.model"
                placeholder="具体模型，如 gpt-4o-mini"
              />
            </ElFormItem>
            <ElFormItem label="模型地址">
              <ElInput
                v-model="draft.model.baseUrl"
                placeholder="自定义模型地址（可选）"
              />
            </ElFormItem>
            <ElFormItem label="密钥">
              <ElInput
                v-model="draft.model.credential"
                placeholder="API Key 或密钥引用（可选）"
                show-password
                type="password"
              />
            </ElFormItem>
          </div>
        </section>

        <section class="prop-section">
          <div class="prop-section-title">调用参数</div>
          <div class="mb-3">
            <ElRadioGroup v-model="draft.parameters.mode">
              <ElRadioButton value="basic">基础模式</ElRadioButton>
              <ElRadioButton value="advanced">高级模式（JSON）</ElRadioButton>
            </ElRadioGroup>
          </div>

          <!-- 基础模式 -->
          <div v-if="draft.parameters.mode === 'basic'" class="prop-grid">
            <ElFormItem label="温度">
              <ElInputNumber
                v-model="draft.parameters.basic.temperature"
                :max="2"
                :min="0"
                :step="0.1"
                controls-position="right"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem label="Top P 采样">
              <ElInputNumber
                v-model="draft.parameters.basic.topP"
                :max="1"
                :min="0"
                :step="0.05"
                controls-position="right"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem label="最大 Token 数">
              <ElInputNumber
                v-model="draft.parameters.basic.maxTokens"
                :min="1"
                controls-position="right"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem label="随机种子">
              <ElInputNumber
                v-model="draft.parameters.basic.seed"
                controls-position="right"
                placeholder="可选"
                style="width: 100%"
              />
            </ElFormItem>
            <ElFormItem label="流式输出">
              <ElSwitch v-model="draft.parameters.basic.stream" />
            </ElFormItem>
          </div>

          <!-- 高级模式：JSON -->
          <div v-else class="prop-grid">
            <ElFormItem class="span-2" label-width="0">
              <div class="w-full">
                <div class="mb-2 flex items-center justify-between">
                  <span class="text-xs text-[var(--el-text-color-secondary)]">
                    直接编辑请求参数（headers / body），不同 Provider 可扩展自己的
                    body 参数
                  </span>
                  <ElButton link size="small" @click="formatAdvanced">
                    格式化
                  </ElButton>
                </div>
                <ElInput
                  v-model="draft.parameters.advanced"
                  :placeholder="LLM_ADVANCED_PLACEHOLDER"
                  :rows="12"
                  class="json-area"
                  type="textarea"
                />
              </div>
            </ElFormItem>
          </div>
        </section>

        <section class="prop-section">
          <div class="prop-section-title">输出</div>
          <div class="prop-grid">
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
            <ElFormItem
              v-if="draft.output.type === 'JSON'"
              class="span-2"
              label="JSON 结构"
            >
              <ElInput
                v-model="draft.output.jsonSchema"
                :rows="8"
                class="json-area"
                placeholder="结构化输出定义（JSON，可选）"
                type="textarea"
              />
            </ElFormItem>
            <ElFormItem class="span-2" label="变量映射">
              <InputMappingEditor
                v-model="draft.output.mapping"
                key-placeholder="输出字段"
                value-placeholder="流程变量"
              />
            </ElFormItem>
          </div>
        </section>
      </template>

      <!-- 提示词配置 -->
      <div v-else-if="section === 'prompt'" class="prop-grid">
        <ElFormItem class="span-2" label="系统提示词">
          <ElInput
            v-model="draft.prompt.systemPrompt"
            :rows="4"
            placeholder="系统提示词"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem class="span-2" label="用户提示词">
          <ElInput
            v-model="draft.prompt.userPromptTemplate"
            :rows="6"
            placeholder="用户提示模板，支持 {{inputs.xxx}} 变量"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem class="span-2" label="提示词变量">
          <InputMappingEditor
            v-model="draft.prompt.variables"
            key-placeholder="变量名"
            value-placeholder="上下文键"
          />
        </ElFormItem>
      </div>

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

/* 属性分类小节：与 StartConfigDialog / PropertyPanel 同款 */
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
