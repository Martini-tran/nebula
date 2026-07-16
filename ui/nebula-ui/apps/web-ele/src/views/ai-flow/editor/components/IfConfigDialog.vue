<script lang="ts" setup>
/**
 * IF 条件节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上 IF 节点右键菜单「配置」触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面按节点类型分发 → 本弹窗 open(node)。
 *
 * 配置「多分支条件判断」：分支列表（可增删 / 上移下移调优先级，顺序即自上而下
 * 首中优先级），每分支内嵌可视化条件构造器；末尾可选 else 兜底（无条件）。
 * 配置结构与读写归一化集中在 ../if-config（落库到 nodeConfig.if）。
 *
 * 确认时归一化写回，并调用 applyIfNodeShape 同步节点尺寸/输出端口，再刷新卡片。
 */
import type { Node } from '@antv/x6';

import type { IfBranch, IfConfig } from '../if-config';

import { computed, reactive, ref } from 'vue';

import {
  ElButton,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElSwitch,
} from 'element-plus';

import { defaultCondGroup } from '../condition';
import { FLOW_DIALOG } from '../constants';
import {
  applyIfNodeShape,
  defaultBranch,
  defaultIfConfig,
  genBranchId,
  normalizeIfConfig,
  serializeIfConfig,
} from '../if-config';
import { refreshNodeCard } from '../shapes/registerShapes';
import ConditionBuilder from './ConditionBuilder.vue';
import EmbeddableDialog from './EmbeddableDialog.vue';

defineOptions({ name: 'IfConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** 确认写回节点后通知外层（NodeConfigPanel → 触发流程保存接口） */
const emit = defineEmits<{ apply: [] }>();

/** IF 节点主题色（与 IfNodeCard 卡片描边一致），驱动小节标题左边条 */
const IF_THEME_COLOR = '#fa8c16';

const visible = ref(false);
let target: Node | undefined;

/** 配置属性草稿：名称 + IF 配置 */
const nameDraft = ref('');
const draft = reactive<IfConfig>(defaultIfConfig());

/** 普通（非 else）分支：可增删排序 */
const normalBranches = computed(() => draft.branches.filter((b) => !b.isElse));
/** else 兜底分支（至多一个） */
const elseBranch = computed(() => draft.branches.find((b) => b.isElse));
const hasElse = computed(() => Boolean(elseBranch.value));

/** 覆盖草稿分支列表（保持 else 恒在末尾） */
function setBranches(branches: IfBranch[]) {
  const normal = branches.filter((b) => !b.isElse);
  const els = branches.find((b) => b.isElse);
  draft.branches = els ? [...normal, els] : normal;
}

function applyDraft(cfg: IfConfig) {
  draft.branches = cfg.branches.map((b) => ({ ...b }));
}

function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  nameDraft.value = (data.name as string) ?? '';
  applyDraft(normalizeIfConfig(data.nodeConfig?.if));
  visible.value = true;
}

// ---------------- 分支编辑 ----------------
function addBranch() {
  const idx = normalBranches.value.length + 1;
  const next = [...draft.branches];
  // 插到 else 之前
  const elseIdx = next.findIndex((b) => b.isElse);
  const br = defaultBranch(`分支${idx}`);
  if (elseIdx === -1) next.push(br);
  else next.splice(elseIdx, 0, br);
  draft.branches = next;
}

function removeBranch(id: string) {
  const normal = normalBranches.value;
  if (normal.length <= 1) {
    ElMessage.warning('至少保留一个条件分支');
    return;
  }
  draft.branches = draft.branches.filter((b) => b.id !== id);
}

/** 上移 / 下移（仅普通分支之间，else 不参与排序） */
function moveBranch(id: string, dir: -1 | 1) {
  const normal = [...normalBranches.value];
  const i = normal.findIndex((b) => b.id === id);
  const j = i + dir;
  if (i < 0 || j < 0 || j >= normal.length) return;
  [normal[i], normal[j]] = [normal[j]!, normal[i]!];
  setBranches(normal);
}

function toggleElse(on: boolean) {
  if (on) {
    if (hasElse.value) return;
    draft.branches = [
      ...draft.branches,
      { id: genBranchId(), label: '否则', condition: defaultCondGroup(), isElse: true },
    ];
  } else {
    draft.branches = draft.branches.filter((b) => !b.isElse);
  }
}

/** 校验：普通分支非空、每个分支至少一条有左值的条件 */
function validate(): boolean {
  const normal = normalBranches.value;
  if (normal.length === 0) {
    ElMessage.error('至少配置一个条件分支');
    return false;
  }
  for (const b of normal) {
    const ok = (b.condition.clauses ?? []).some((c) => (c.left ?? '').trim());
    if (!ok) {
      ElMessage.error(`「${b.label}」缺少有效条件（左值不能为空）`);
      return false;
    }
  }
  return true;
}

function handleConfirm() {
  if (!validate()) return;
  if (target) {
    const cfg = serializeIfConfig(draft);
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = { ...data.nodeConfig, if: cfg };
    target.setData(
      { ...data, name: nameDraft.value.trim(), nodeConfig },
      { overwrite: true },
    );
    // 同步节点尺寸 + 动态输出端口（每分支一口），再刷新卡片
    applyIfNodeShape(target, cfg.branches);
    refreshNodeCard(target);
  }
  emit('apply');
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
    :dialog-class="`${FLOW_DIALOG.class} if-config-dialog`"
    title="条件判断配置"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-position="top"
      :style="{ '--type-color': IF_THEME_COLOR }"
      @submit.prevent
    >
      <!-- 基础：名称 -->
      <div class="prop-grid-1">
        <ElFormItem label="名称">
          <ElInput
            v-model="nameDraft"
            maxlength="64"
            placeholder="卡片标题，缺省显示「条件判断」"
          />
        </ElFormItem>
      </div>

      <ElDivider />

      <!-- 分支：自上而下首个命中生效（分支卡片列表 + 条件构造器） -->
      <div class="group-title">分支（自上而下首个命中生效）</div>
      <div v-for="(b, i) in normalBranches" :key="b.id" class="branch-card">
        <div class="branch-head">
          <span class="branch-order">{{ i + 1 }}</span>
          <ElInput
            v-model="b.label"
            class="branch-name"
            maxlength="32"
            placeholder="分支名"
            size="small"
          />
          <div class="branch-ops">
            <ElButton
              :disabled="i === 0"
              link
              size="small"
              @click="moveBranch(b.id, -1)"
            >
              上移
            </ElButton>
            <ElButton
              :disabled="i === normalBranches.length - 1"
              link
              size="small"
              @click="moveBranch(b.id, 1)"
            >
              下移
            </ElButton>
            <ElButton
              link
              size="small"
              type="danger"
              @click="removeBranch(b.id)"
            >
              删除
            </ElButton>
          </div>
        </div>
        <ConditionBuilder v-model="b.condition" />
      </div>

      <ElButton class="add-branch-btn" size="small" @click="addBranch">
        + 添加分支
      </ElButton>

      <ElDivider />

      <!-- 兜底：else 开关 + 名称 -->
      <div class="prop-grid-2">
        <ElFormItem label="else 分支">
          <ElSwitch
            :model-value="hasElse"
            @update:model-value="toggleElse($event as boolean)"
          />
          <span class="hint">开启后，无分支命中时走 else（无需条件）</span>
        </ElFormItem>
        <ElFormItem v-if="hasElse && elseBranch" label="else 名称">
          <ElInput
            v-model="elseBranch.label"
            maxlength="32"
            placeholder="否则"
          />
        </ElFormItem>
      </div>
    </ElForm>

    <template #footer>
      <ElButton v-if="!embedded" @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">
        {{ embedded ? '应用配置' : '保存配置' }}
      </ElButton>
    </template>
  </EmbeddableDialog>
</template>

<style scoped>
/* ===== 呼吸感：容器内边距 + 分组/表单项间距（与 LlmConfigDialog 同款规范） ===== */

.prop-form {
  padding: 8px 4px;
}

/* 表单项垂直间距 ≥20px */
.prop-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

/* 标签在上：标签与输入框间距 6-8px；标签字重/字号提升可读性 */
.prop-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}

/* 逻辑分组之间：分割线 + 32px 呼吸间距 */
.prop-form :deep(.el-divider) {
  margin: 32px 0;
}

/* 非表单项的分组标题（分支列表用），与标签同字重字号 */
.group-title {
  margin-bottom: 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

/* ===== 布局：长项单列 / 短项两列 ===== */
.prop-grid-1 {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.prop-grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
}

.prop-grid-1 :deep(.el-form-item),
.prop-grid-2 :deep(.el-form-item) {
  min-width: 0;
}

/* ===== 视觉层级：输入框统一高度 40-44px / 圆角 8px / 五态 ===== */

.prop-form :deep(.el-input__wrapper),
.prop-form :deep(.el-select__wrapper) {
  border-radius: 8px;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
}

/* 顶层表单项的输入框统一 42px 高（分支卡片内 small 尺寸不拔高，排除之） */
.prop-form > .prop-grid-1 :deep(.el-input__wrapper),
.prop-form > .prop-grid-2 :deep(.el-input__wrapper),
.prop-form > .prop-grid-1 :deep(.el-select__wrapper),
.prop-form > .prop-grid-2 :deep(.el-select__wrapper) {
  min-height: 42px;
}

/* hover：边框中性灰略深 */
.prop-form :deep(.el-input__wrapper:hover),
.prop-form :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-border-color-hover) inset;
}

/* focus：主题色边框 + 外发光阴影 */
.prop-form :deep(.el-input__wrapper.is-focus),
.prop-form :deep(.el-select__wrapper.is-focused) {
  box-shadow:
    0 0 0 1px var(--type-color, var(--el-color-primary)) inset,
    0 0 0 3px color-mix(in srgb, var(--type-color, var(--el-color-primary)) 20%, transparent);
}

/* disabled：置灰、禁用光标 */
.prop-form :deep(.el-input.is-disabled .el-input__wrapper),
.prop-form :deep(.el-select__wrapper.is-disabled) {
  background: var(--el-disabled-bg-color);
  box-shadow: 0 0 0 1px var(--el-disabled-border-color) inset;
  cursor: not-allowed;
}

/* error：红框 */
.prop-form :deep(.el-form-item.is-error .el-input__wrapper),
.prop-form :deep(.el-form-item.is-error .el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

/* success：绿框 */
.prop-form :deep(.el-form-item.is-success .el-input__wrapper),
.prop-form :deep(.el-form-item.is-success .el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-success) inset;
}

/* 错误提示：紧贴输入框下方的红色小字（禁用弹窗） */
.prop-form :deep(.el-form-item__error) {
  padding-top: 4px;
  font-size: 12px;
}

/* 占位符：比正文浅 2 级 */
.prop-form :deep(.el-input__inner::placeholder) {
  color: var(--el-text-color-placeholder);
}

/* 添加分支按钮：与分支卡片留出呼吸间距 */
.add-branch-btn {
  margin-top: 4px;
}

/* 单个分支块 */
.branch-card {
  padding: 10px 12px;
  margin-bottom: 10px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.branch-head {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.branch-order {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: 11px;
  font-weight: 600;
  color: #d46b08;
  background: #ffe7ba;
  border-radius: 4px;
}

.branch-name {
  flex: 0 0 200px;
}

.branch-ops {
  display: flex;
  gap: 2px;
  align-items: center;
  margin-left: auto;
}

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

<!--
  非 scoped：ElDialog append-to-body 到 <body>，scoped 选择器穿透不到 .el-dialog。
  让条件判断配置弹窗可由用户拖拽右下角自由缩放（宽 + 高），与 LlmConfigDialog 一致。
-->
<style>
.if-config-dialog.el-dialog {
  display: flex;
  flex-direction: column;
  min-width: 480px;
  max-width: 96vw;
  min-height: 320px;
  max-height: 92vh;
  overflow: hidden;
  resize: both;
}

.if-config-dialog.el-dialog .el-dialog__header,
.if-config-dialog.el-dialog .el-dialog__footer {
  flex-shrink: 0;
}

.if-config-dialog.el-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
}
</style>
