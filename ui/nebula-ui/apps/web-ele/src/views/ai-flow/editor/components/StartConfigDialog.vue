<script lang="ts" setup>
/**
 * 开始节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上开始节点右上角的「配置」徽标触发：卡片 → graph.trigger('start:config')
 * → useFlowGraph → 编辑器主页面 → 本弹窗 open(node)。
 *
 * 布局与 PropertyPanel 同规格（820px / top 5vh / flow-prop-dialog 限高滚动），
 * 单面板分节呈现（不用 tab）：
 *   - 配置属性：节点通用属性（名称等，写回 node.data）
 *   - 入参：直接编辑 JSON 对象（键为入参标识、值为默认值，写回
 *     nodeConfig.inputs），确认时校验必须是合法 JSON 对象；类型按值推断
 *     （消费侧统一走 normalizeStartInputs）
 *
 * 弹窗自持草稿：打开时从节点读入，确认时校验后写回并刷新卡片；取消丢弃。
 */
import type { Node } from '@antv/x6';

import { reactive, ref } from 'vue';

import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

import { refreshNodeCard } from '../shapes/registerShapes';

defineOptions({ name: 'StartConfigDialog' });

/** 开始节点主题色（与 StartNodeCard 卡片描边一致），驱动小节标题左边条 */
const START_THEME_COLOR = '#13c2c2';

const visible = ref(false);
let target: Node | undefined;

/** 配置属性草稿 */
const propsDraft = reactive({ name: '' });
/** 入参 JSON 草稿 */
const inputsText = ref('');

/** 打开弹窗：读入目标节点当前配置为草稿 */
function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  propsDraft.name = (data.name as string) ?? '';
  const inputs = data.nodeConfig?.inputs;
  inputsText.value =
    inputs && typeof inputs === 'object' && Object.keys(inputs).length > 0
      ? JSON.stringify(inputs, null, 2)
      : '';
  visible.value = true;
}

/**
 * 解析并校验入参 JSON。空文本视为无入参；必须是 JSON 对象
 * （键为入参标识、值为默认值），数组或标量均拒绝。
 */
function parseInputs():
  | { inputs: Record<string, any>; ok: true }
  | { message: string; ok: false } {
  const text = inputsText.value.trim();
  if (!text) return { inputs: {}, ok: true };

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { message: '入参不是合法 JSON', ok: false };
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return { message: '入参必须是 JSON 对象（key: value）', ok: false };
  }
  return { inputs: parsed as Record<string, any>, ok: true };
}

/** 格式化入参 JSON（非法时提示，不改动原文） */
function formatInputs() {
  const text = inputsText.value.trim();
  if (!text) return;
  try {
    inputsText.value = JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    ElMessage.warning('入参不是合法 JSON，无法格式化');
  }
}

/** 确认：校验入参 JSON，通过后写回节点并刷新卡片 */
function handleConfirm() {
  const result = parseInputs();
  if (!result.ok) {
    ElMessage.error(result.message);
    return;
  }
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = { ...data.nodeConfig, inputs: result.inputs };
    target.setData(
      { ...data, name: propsDraft.name.trim(), nodeConfig },
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
  <!-- flow-prop-dialog：与 PropertyPanel 同一全局规格（body 限高 78vh 滚动） -->
  <ElDialog
    v-model="visible"
    append-to-body
    class="flow-prop-dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    destroy-on-close
    title="开始节点配置"
    top="5vh"
    width="820px"
  >
    <ElForm
      class="prop-form"
      label-width="84px"
      :style="{ '--type-color': START_THEME_COLOR }"
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
              placeholder="卡片标题，缺省显示「开始」"
            />
          </ElFormItem>
        </div>
      </section>

      <!-- 入参：直接编辑 JSON -->
      <section class="prop-section">
        <div class="prop-section-title">入参（JSON）</div>
        <div class="prop-grid">
          <ElFormItem class="span-2" label-width="0">
            <div class="w-full">
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs text-[var(--el-text-color-secondary)]">
                  JSON 对象：键为入参标识（key），值为默认值，类型按值自动推断
                </span>
                <ElButton link size="small" @click="formatInputs">
                  格式化
                </ElButton>
              </div>
              <ElInput
                v-model="inputsText"
                :rows="12"
                class="inputs-json"
                placeholder="JSON 对象，为空表示无入参"
                type="textarea"
              />
              <ElAlert
                class="mt-2"
                :closable="false"
                title="入参在流程启动时注入上下文，下游节点通过标识（key）引用"
                type="info"
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

.inputs-json :deep(textarea) {
  font-family: 'JetBrains Mono', consolas, monaco, monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
