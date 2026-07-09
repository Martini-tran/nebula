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
import EmbeddableDialog from './EmbeddableDialog.vue';
import {
  defaultJoinConfig,
  JOIN_TEMPLATE_PLACEHOLDER,
  normalizeJoinConfig,
  serializeJoinConfig,
} from '../join-config';
import { refreshNodeCard } from '../shapes/registerShapes';

defineOptions({ name: 'JoinConfigDialog' });

/** embedded：内嵌到 NodeConfigDrawer 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

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
    title="汇总配置"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-width="88px"
      :style="{ '--type-color': JOIN_THEME_COLOR }"
      @submit.prevent
    >
      <section class="prop-section">
        <div class="prop-section-title">基础</div>
        <div class="prop-grid">
          <ElFormItem label="名称">
            <ElInput
              v-model="nameDraft"
              maxlength="64"
              placeholder="卡片标题，缺省显示「汇总」"
            />
          </ElFormItem>
        </div>
      </section>

      <section class="prop-section">
        <div class="prop-section-title">汇聚策略</div>
        <div class="prop-grid">
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
            />
            <span class="hint">毫秒，0 = 不限</span>
          </ElFormItem>
        </div>
      </section>

      <section class="prop-section">
        <div class="prop-section-title">输出</div>
        <div class="prop-grid">
          <ElFormItem label="合并模式">
            <ElSelect v-model="draft.output.merge">
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
          <ElFormItem
            v-if="draft.output.merge === 'TEMPLATE'"
            class="span-2"
            label="模板"
          >
            <div class="w-full">
              <div class="mb-1">
                <span class="hint no-indent">{{ TEMPLATE_HINT }}</span>
              </div>
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

/* 属性分类小节：与 LlmConfigDialog / AgentConfigDialog 同款 */
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

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.hint.no-indent {
  margin-left: 0;
}

.tpl-area :deep(textarea) {
  font-family: 'JetBrains Mono', consolas, monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
