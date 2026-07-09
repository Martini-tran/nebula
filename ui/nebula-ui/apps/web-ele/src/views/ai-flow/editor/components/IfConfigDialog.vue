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

import { ElButton, ElForm, ElFormItem, ElInput, ElMessage, ElSwitch } from 'element-plus';

import { defaultCondGroup } from '../condition';
import { FLOW_DIALOG } from '../constants';
import EmbeddableDialog from './EmbeddableDialog.vue';
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

defineOptions({ name: 'IfConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

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
    title="条件判断配置"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-width="88px"
      :style="{ '--type-color': IF_THEME_COLOR }"
      @submit.prevent
    >
      <section class="prop-section">
        <div class="prop-section-title">基础</div>
        <div class="prop-grid">
          <ElFormItem label="名称">
            <ElInput
              v-model="nameDraft"
              maxlength="64"
              placeholder="卡片标题，缺省显示「条件判断」"
            />
          </ElFormItem>
        </div>
      </section>

      <section class="prop-section">
        <div class="prop-section-title">分支（自上而下首个命中生效）</div>

        <div
          v-for="(b, i) in normalBranches"
          :key="b.id"
          class="branch-card"
        >
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

        <ElButton class="mt-1" size="small" @click="addBranch">
          + 添加分支
        </ElButton>
      </section>

      <section class="prop-section">
        <div class="prop-section-title">兜底</div>
        <div class="prop-grid">
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

.mt-1 {
  margin-top: 4px;
}
</style>
