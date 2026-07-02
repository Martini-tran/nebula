<script lang="ts" setup>
/**
 * 开始节点入参配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上开始节点右上角的配置齿轮触发：卡片 → graph.trigger('start:config')
 * → useFlowGraph → 编辑器主页面 → 本弹窗 open(node)。本弹窗自持一份入参草稿，
 * 打开时从 node.data.nodeConfig.inputs 深拷贝读入，确认时写回并刷新卡片摘要；
 * 取消则丢弃草稿，不触碰节点。
 *
 * 与属性面板的分工：属性面板负责各类节点的通用属性编辑；开始节点的入参因结构
 * 复杂（JSON 编辑器 + 校验规则）单独走此弹窗，二者互不感知。
 */
import type { Node } from '@antv/x6';

import type { StartInputParam } from './StartInputsEditor.vue';

import { ref } from 'vue';

import { ElAlert, ElButton, ElDialog } from 'element-plus';

import { refreshNodeCard } from '../shapes/registerShapes';
import StartInputsEditor from './StartInputsEditor.vue';

defineOptions({ name: 'StartConfigDialog' });

const visible = ref(false);
const draft = ref<StartInputParam[]>([]);
let target: Node | undefined;

/** 深拷贝入参数组，避免弹窗草稿与节点数据共享引用 */
function cloneInputs(inputs?: StartInputParam[]): StartInputParam[] {
  if (!Array.isArray(inputs)) return [];
  return inputs.map((it) => ({ ...it, validation: { ...it.validation } }));
}

/** 打开弹窗：读入目标节点当前入参为草稿 */
function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  draft.value = cloneInputs(data.nodeConfig?.inputs as StartInputParam[]);
  visible.value = true;
}

/** 确认：过滤空项后写回 node.data.nodeConfig.inputs，并刷新卡片摘要 */
function handleConfirm() {
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const inputs = (draft.value ?? []).filter((it) => it.name || it.key);
    const nodeConfig = { ...(data.nodeConfig ?? {}), inputs };
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
    title="配置流程入参"
    width="720px"
    :close-on-click-modal="false"
    append-to-body
    destroy-on-close
  >
    <StartInputsEditor v-model="draft" />
    <ElAlert
      class="mt-3"
      :closable="false"
      title="入参在流程启动时注入上下文，可被下游节点通过标识（key）引用"
      type="info"
    />
    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">确定</ElButton>
    </template>
  </ElDialog>
</template>
