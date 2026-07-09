<script lang="ts" setup>
/**
 * FOR 循环容器配置弹窗（独立弹窗）。
 *
 * 由循环容器框头齿轮触发：LoopGroupCard → graph.trigger('start:menu', key='loop-config')
 * → useFlowGraph → 编辑器主页面分发 → 本弹窗 open(node)。
 *
 * 配置：循环模式（次数 COUNT / 遍历 FOREACH 预留）+ 循环次数 + 安全上限 +
 * 状态跳出（break 开关 + 复用条件构造器的跳出条件）。
 * 配置结构与读写归一化集中在 ../loop-config（落库到 nodeConfig.loop）。
 */
import type { Node } from '@antv/x6';

import type { LoopConfig } from '../loop-config';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { defaultCondGroup } from '../condition';
import { collectUpstreamVars } from '../composables/useUpstreamVars';
import { FLOW_DIALOG } from '../constants';
import EmbeddableDialog from './EmbeddableDialog.vue';
import {
  defaultLoopConfig,
  LOOP_MODES,
  normalizeLoopConfig,
  serializeLoopConfig,
} from '../loop-config';
import { refreshNodeCard } from '../shapes/registerShapes';
import ConditionBuilder from './ConditionBuilder.vue';

defineOptions({ name: 'LoopConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** 循环容器主题色（与 LoopGroupCard 虚线框一致），驱动小节标题左边条 */
const LOOP_THEME_COLOR = '#5f95ff';

const visible = ref(false);
let target: Node | undefined;

const nameDraft = ref('');
const draft = reactive<LoopConfig>(defaultLoopConfig());

/** 上游可用变量（FOREACH「遍历列表」下拉选取列表变量，替代手打） */
const upstreamVars = ref<ReturnType<typeof collectUpstreamVars>>([]);

function applyDraft(cfg: LoopConfig) {
  Object.assign(draft, cfg);
  // reactive 对象 breakCondition 需替换引用触发 ConditionBuilder 更新
  draft.breakCondition = cfg.breakCondition ?? defaultCondGroup();
}

function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  nameDraft.value = (data.name as string) ?? '';
  // 收集上游可用变量（供 FOREACH 遍历列表下拉）；graph 从节点自身取
  upstreamVars.value = collectUpstreamVars(node.model?.graph, node);
  applyDraft(normalizeLoopConfig(data.nodeConfig?.loop));
  visible.value = true;
}

function handleConfirm() {
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = { ...data.nodeConfig, loop: serializeLoopConfig(draft) };
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
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="FLOW_DIALOG.class"
    title="循环配置"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-width="96px"
      :style="{ '--type-color': LOOP_THEME_COLOR }"
      @submit.prevent
    >
      <section class="prop-section">
        <div class="prop-section-title">基础</div>
        <div class="prop-grid">
          <ElFormItem label="名称">
            <ElInput
              v-model="nameDraft"
              maxlength="64"
              placeholder="卡片标题，缺省显示「循环」"
            />
          </ElFormItem>
          <ElFormItem label="循环模式">
            <ElSelect v-model="draft.mode" style="width: 100%">
              <ElOption
                v-for="opt in LOOP_MODES"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="draft.mode === 'COUNT'" label="循环次数">
            <ElInputNumber
              v-model="draft.count"
              :min="1"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem v-else label="遍历列表">
            <ElSelect
              v-model="draft.itemsExpr"
              allow-create
              clearable
              default-first-option
              filterable
              placeholder="选上游列表变量，或手动输入变量键"
              style="width: 100%"
            >
              <ElOption
                v-for="v in upstreamVars"
                :key="v.key"
                :label="v.label"
                :value="v.key"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="最大迭代">
            <ElInputNumber
              v-model="draft.maxIterations"
              :min="1"
              controls-position="right"
              placeholder="防死循环上限"
              style="width: 100%"
            />
          </ElFormItem>
        </div>
      </section>

      <section class="prop-section">
        <div class="prop-section-title">状态跳出</div>
        <div class="prop-grid">
          <ElFormItem label="启用跳出">
            <ElSwitch v-model="draft.breakEnabled" />
            <span class="hint">满足条件时提前 break 出循环</span>
          </ElFormItem>
          <ElFormItem
            v-if="draft.breakEnabled && draft.breakCondition"
            class="span-2"
            label="跳出条件"
          >
            <ConditionBuilder v-model="draft.breakCondition" />
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
</style>
