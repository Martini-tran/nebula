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

import type { AiPromptApi } from '#/api';

import type { LlmConfig } from '../llm-config';

import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInput,
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

/**
 * 提示词正文展开态：文本框默认折叠，点击「编辑正文」才展开。
 * 选中提示词会带出正文填入草稿，但仍需展开才可见/可改。
 */
const systemPromptOpen = ref(false);
const userPromptOpen = ref(false);

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
  // 正文默认折叠，每次打开都收起
  systemPromptOpen.value = false;
  userPromptOpen.value = false;
  section.value = target_section;
  visible.value = true;
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

/** 确认：归一化写回 nodeConfig.llm，同步顶层 profileCode，刷新卡片 */
function handleConfirm() {
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
      class="prop-form"
      label-width="128px"
      :style="{ '--type-color': LLM_THEME_COLOR }"
      @submit.prevent
    >
      <ElTabs v-model="activeTab" class="llm-tabs">
        <!-- 基础配置：模型档案 + 提示词 + 输出（小节间用分割线区分） -->
        <ElTabPane label="基础配置" name="basic">
          <div class="prop-grid">
            <ElFormItem class="span-2" label="模型档案">
              <ProfileSelector
                v-model="draft.model.profileCode"
                class="w-full"
                placeholder="选择模型档案（模型、地址、密钥、参数均由档案承载）"
              />
            </ElFormItem>
          </div>

          <ElDivider />
          <div class="prop-grid">
            <ElFormItem class="span-2" label="系统提示词">
              <PromptSelector
                v-model="draft.prompt.systemPromptCode"
                class="w-full"
                placeholder="从提示词管理选择（system），带出正文到下方（可覆盖）"
                role="system"
                @change="onSystemPromptChange"
              />
            </ElFormItem>
            <ElFormItem class="span-2" label-width="0">
              <div class="w-full">
                <!-- 折叠入口：点击展开/收起正文文本框 -->
                <button
                  class="prompt-toggle"
                  type="button"
                  @click="systemPromptOpen = !systemPromptOpen"
                >
                  <span class="prompt-toggle-arrow" :class="{ open: systemPromptOpen }">▸</span>
                  <span>{{ systemPromptOpen ? '收起正文' : '编辑正文' }}</span>
                  <span v-if="!systemPromptOpen && draft.prompt.systemPrompt" class="prompt-toggle-hint">
                    （已有内容）
                  </span>
                </button>
                <ElInput
                  v-show="systemPromptOpen"
                  v-model="draft.prompt.systemPrompt"
                  class="mt-2"
                  :rows="4"
                  placeholder="系统提示词正文"
                  type="textarea"
                />
              </div>
            </ElFormItem>
            <ElFormItem class="span-2" label="用户提示词">
              <PromptSelector
                v-model="draft.prompt.userPromptCode"
                class="w-full"
                placeholder="从提示词管理选择（user），带出正文到下方（可覆盖）"
                role="user"
                @change="onUserPromptChange"
              />
            </ElFormItem>
            <ElFormItem class="span-2" label-width="0">
              <div class="w-full">
                <button
                  class="prompt-toggle"
                  type="button"
                  @click="userPromptOpen = !userPromptOpen"
                >
                  <span class="prompt-toggle-arrow" :class="{ open: userPromptOpen }">▸</span>
                  <span>{{ userPromptOpen ? '收起正文' : '编辑正文' }}</span>
                  <span v-if="!userPromptOpen && draft.prompt.userPromptTemplate" class="prompt-toggle-hint">
                    （已有内容）
                  </span>
                </button>
                <ElInput
                  v-show="userPromptOpen"
                  v-model="draft.prompt.userPromptTemplate"
                  class="mt-2"
                  :rows="6"
                  placeholder="用户提示模板，支持 {{inputs.xxx}} 变量"
                  type="textarea"
                />
              </div>
            </ElFormItem>
          </div>

          <ElDivider />
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
          </div>
        </ElTabPane>

        <!-- 高级配置：上下文 + 调用参数（小节间用分割线区分） -->
        <ElTabPane label="高级配置" name="advanced">
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

          <ElDivider />
          <div class="prop-grid">
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

/* 小节分割线：收紧上下间距 */
.llm-tabs :deep(.el-divider) {
  margin: 16px 0;
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

/* 提示词正文折叠入口：一行可点击的展开/收起条 */
.prompt-toggle {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 0;
  font-size: 13px;
  color: var(--type-color, var(--el-color-primary));
  cursor: pointer;
  background: none;
  border: none;
}

.prompt-toggle:hover {
  opacity: 0.8;
}

.prompt-toggle-arrow {
  display: inline-block;
  font-size: 11px;
  transition: transform 0.15s;
}

.prompt-toggle-arrow.open {
  transform: rotate(90deg);
}

.prompt-toggle-hint {
  color: var(--el-text-color-secondary);
}

</style>
