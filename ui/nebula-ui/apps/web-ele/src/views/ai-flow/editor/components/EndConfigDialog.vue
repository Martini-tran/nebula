<script lang="ts" setup>
/**
 * END 结束节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上 END 节点右键菜单「配置」触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面按节点类型分发 → 本弹窗 open(node)。
 *
 * 配置流程的**固定格式 JSON 输出**，分两节：
 *   - 基础：名称
 *   - 输出 JSON：直接编辑 JSON 对象模板（键=输出字段，字符串值可用
 *     {{outputKey}} 引用上游输出），确认时校验必须是合法 JSON 对象
 * 配置结构与读写归一化集中在 ../end-config（落库到 nodeConfig.end）。
 *
 * 弹窗自持草稿：打开时从节点读入并补全缺省，确认时校验后写回并刷新卡片；
 * 取消丢弃草稿。名称与 end 配置一并写回。
 */
import type { Node } from '@antv/x6';

import type { EndConfig } from '../end-config';

import { reactive, ref } from 'vue';

import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus';

import { FLOW_DIALOG } from '../constants';
import EmbeddableDialog from './EmbeddableDialog.vue';
import {
  defaultEndConfig,
  END_OUTPUT_PLACEHOLDER,
  normalizeEndConfig,
  parseEndOutputJson,
  serializeEndConfig,
} from '../end-config';
import { refreshNodeCard } from '../shapes/registerShapes';

defineOptions({ name: 'EndConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** END 节点主题色（与 EndNodeCard 卡片描边一致），驱动小节标题左边条 */
const END_THEME_COLOR = '#5f95ff';

/**
 * 输出模板说明文案。变量示例含双花括号，直接写进模板会被 Vue 编译器当
 * 嵌套插值解析报错，故提到常量里以整段文本插值。
 */
const OUTPUT_HINT =
  '固定输出结构（JSON 对象）：键=输出字段，字符串值可用 {{outputKey}} 引用上游输出';

const visible = ref(false);
let target: Node | undefined;

/** 配置属性草稿：名称 + END 配置 */
const nameDraft = ref('');
const draft = reactive<EndConfig>(defaultEndConfig());

/** 打开弹窗：读入目标节点当前配置为草稿（缺省字段由 normalize 补全） */
function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  nameDraft.value = (data.name as string) ?? '';
  Object.assign(draft, normalizeEndConfig(data.nodeConfig?.end));
  visible.value = true;
}

/** 格式化输出 JSON（非法时提示，不改动原文） */
function formatOutput() {
  const text = draft.outputJson.trim();
  if (!text) return;
  try {
    draft.outputJson = JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    ElMessage.warning('输出模板不是合法 JSON，无法格式化');
  }
}

/** 确认：校验输出 JSON 模板，通过后归一化写回 nodeConfig.end 与名称，刷新卡片 */
function handleConfirm() {
  const result = parseEndOutputJson(draft.outputJson);
  if (!result.ok) {
    ElMessage.error(result.message);
    return;
  }
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = {
      ...data.nodeConfig,
      end: serializeEndConfig(draft),
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
  <!-- FLOW_DIALOG：流程编辑器弹窗统一规格；embedded 时去弹窗外壳内嵌抽屉 -->
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="FLOW_DIALOG.class"
    title="结束节点配置"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-width="88px"
      :style="{ '--type-color': END_THEME_COLOR }"
      @submit.prevent
    >
      <section class="prop-section">
        <div class="prop-section-title">基础</div>
        <div class="prop-grid">
          <ElFormItem label="名称">
            <ElInput
              v-model="nameDraft"
              maxlength="64"
              placeholder="卡片标题，缺省显示「结束」"
            />
          </ElFormItem>
        </div>
      </section>

      <section class="prop-section">
        <div class="prop-section-title">输出 JSON</div>
        <div class="prop-grid">
          <ElFormItem class="span-2" label-width="0">
            <div class="w-full">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-[var(--el-text-color-secondary)]">
                  {{ OUTPUT_HINT }}
                </span>
                <ElButton link size="small" @click="formatOutput">
                  格式化
                </ElButton>
              </div>
              <ElInput
                v-model="draft.outputJson"
                :placeholder="END_OUTPUT_PLACEHOLDER"
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

/* 属性分类小节：与 StartConfigDialog / AgentConfigDialog 同款 */
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
