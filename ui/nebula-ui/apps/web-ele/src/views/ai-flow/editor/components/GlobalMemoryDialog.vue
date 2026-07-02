<script lang="ts" setup>
/**
 * 开始节点「全局记忆」配置弹窗（独立弹窗，与 StartConfigDialog 同一套
 * prop-section 样式体系）。
 *
 * 由开始节点右键菜单「全局记忆」触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面 → 本弹窗 open(node)。
 *
 * 字段：
 *   - 是否开启全局记忆（开关）
 *   - 保存记忆策略（全量保存 / LLM模型保存 / Agent保存）
 *
 * 落库：写回 node.data.nodeConfig.memory = { enabled, strategy }；
 * 卡片「记忆」指示点按 enabled === true 亮起。取消丢弃草稿。
 */
import type { Node } from '@antv/x6';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { FLOW_DIALOG } from '../constants';
import { refreshNodeCard } from '../shapes/registerShapes';

defineOptions({ name: 'GlobalMemoryDialog' });

/** 开始节点主题色（与 StartNodeCard 卡片描边一致），驱动小节标题左边条 */
const START_THEME_COLOR = '#13c2c2';

/** 保存记忆策略候选 */
const STRATEGY_OPTIONS = [
  { label: '全量保存', value: 'FULL' },
  { label: 'LLM模型保存', value: 'LLM' },
  { label: 'Agent保存', value: 'AGENT' },
] as const;

type MemoryStrategy = (typeof STRATEGY_OPTIONS)[number]['value'];

const visible = ref(false);
let target: Node | undefined;

const draft = reactive<{ enabled: boolean; strategy: MemoryStrategy }>({
  enabled: false,
  strategy: 'FULL',
});

/** 打开弹窗：读入目标节点当前记忆配置为草稿 */
function open(node: Node) {
  target = node;
  const memory =
    (node.getData<Record<string, any>>()?.nodeConfig?.memory as
      | Record<string, any>
      | undefined) ?? {};
  draft.enabled = memory.enabled === true;
  draft.strategy = (memory.strategy as MemoryStrategy) || 'FULL';
  visible.value = true;
}

/** 确认：写回 nodeConfig.memory 并刷新卡片指示点 */
function handleConfirm() {
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = {
      ...data.nodeConfig,
      memory: { enabled: draft.enabled, strategy: draft.strategy },
    };
    target.setData({ ...data, nodeConfig }, { overwrite: true });
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
  <ElDialog
    v-model="visible"
    append-to-body
    :class="FLOW_DIALOG.class"
    :close-on-click-modal="false"
    destroy-on-close
    title="全局记忆"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-width="110px"
      :style="{ '--type-color': START_THEME_COLOR }"
      @submit.prevent
    >
      <section class="prop-section">
        <div class="prop-section-title">记忆配置</div>
        <ElFormItem label="开启全局记忆">
          <ElSwitch v-model="draft.enabled" />
        </ElFormItem>
        <ElFormItem label="保存记忆策略">
          <ElSelect
            v-model="draft.strategy"
            :disabled="!draft.enabled"
            style="width: 100%"
          >
            <ElOption
              v-for="opt in STRATEGY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
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

/* 属性分类小节：与 StartConfigDialog / PropertyPanel 同款 */
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
