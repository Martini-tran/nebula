<script lang="ts" setup>
/**
 * LLM 节点配置弹窗（独立弹窗，仿 StartConfigDialog，不与 PropertyPanel 耦合）。
 *
 * 由画布上 LLM 节点右键菜单「配置」触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面按节点类型分发 → 本弹窗 open(node)。
 *
 * 布局与 StartConfigDialog 同规格（820px / top 5vh / flow-prop-dialog 限高滚动）。
 * 当前仅「名称」一项（先打通链路），后续再补模型档案 / 提示词等字段。
 *
 * 弹窗自持草稿：打开时从节点读入，确认时写回并刷新卡片；取消丢弃。
 */
import type { Node } from '@antv/x6';

import { reactive, ref } from 'vue';

import { ElButton, ElDialog, ElForm, ElFormItem, ElInput } from 'element-plus';

import { FLOW_DIALOG } from '../constants';
import { refreshNodeCard } from '../shapes/registerShapes';

defineOptions({ name: 'LlmConfigDialog' });

/** LLM 节点主题色（与 LlmNodeCard 卡片描边一致），驱动小节标题左边条 */
const LLM_THEME_COLOR = '#13c2c2';

const visible = ref(false);
let target: Node | undefined;

/** 配置属性草稿 */
const propsDraft = reactive({ name: '' });

/** 打开弹窗：读入目标节点当前配置为草稿 */
function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  propsDraft.name = (data.name as string) ?? '';
  visible.value = true;
}

/** 确认：写回节点并刷新卡片 */
function handleConfirm() {
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    target.setData(
      { ...data, name: propsDraft.name.trim() },
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
    title="LLM 节点配置"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-width="84px"
      :style="{ '--type-color': LLM_THEME_COLOR }"
      @submit.prevent
    >
      <!-- 配置属性 -->
      <section class="prop-section">
        <div class="prop-section-title">配置属性</div>
        <div class="prop-grid">
          <ElFormItem label="名称">
            <ElInput
              v-model="propsDraft.name"
              maxlength="64"
              placeholder="卡片标题，缺省显示「LLM」"
            />
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

/* 属性分类小节：与 PropertyPanel 同款 */
.prop-section {
  padding: 4px 0 2px;
}

.prop-section-title {
  padding-left: 8px;
  margin: 6px 0 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  border-left: 3px solid var(--type-color, var(--el-color-primary));
}
</style>
