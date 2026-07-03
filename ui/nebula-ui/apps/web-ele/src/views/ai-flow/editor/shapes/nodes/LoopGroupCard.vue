<script lang="ts" setup>
/**
 * FOR 循环容器卡片（X6 vue-shape，虚线分组框）。
 *
 * 框选一组节点后生成的容器：一个大虚线矩形把成员节点包住（X6 embedding 父节点），
 * 框头一条 bar 显示「FOR ×N · break」+ 齿轮（打开 LoopConfigDialog）+ 解散按钮。
 *
 * 关键：框体（虚线框内部）必须 pointer-events:none，让内部子节点可点选/拖动；
 * 只有框头 bar 接收事件（配置 / 解散 / 右键菜单）。数据由壳组件 FlowNodeCard 计算
 * 摘要后传入（summary），本卡片纯展示。
 */
import type { Node } from '@antv/x6';

import type { AiFlowApi } from '#/api';

import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue';

import { loopSummary, normalizeLoopConfig } from '../../loop-config';

defineOptions({ name: 'LoopGroupCard' });

const getNode = inject<() => Node>('getNode');
const data = ref<AiFlowApi.FlowNodeRaw & { __run_state?: string }>(
  {} as AiFlowApi.FlowNodeRaw,
);
let node: Node | undefined;

function sync() {
  data.value = (node?.getData() ?? {}) as AiFlowApi.FlowNodeRaw;
}

onMounted(() => {
  node = getNode?.();
  sync();
  node?.on('change:data', sync);
});
onBeforeUnmount(() => node?.off('change:data', sync));

const label = computed(() => data.value.name || '循环');
const summary = computed(() =>
  loopSummary(normalizeLoopConfig(data.value.nodeConfig?.loop)),
);
const dimmed = computed(() => data.value.__run_state === 'skipped');
const borderColor = computed(() =>
  data.value.__run_state === 'skipped' ? '#c0c4cc' : '#5f95ff',
);

/**
 * 框头动作经 graph.trigger('start:menu') 抛给编辑器主页面分发（与其它节点同一桥）：
 * loop-config → LoopConfigDialog；loop-dissolve → 解散循环。
 * vue-shape 卡片在 X6 独立树里，emit 不冒泡，图事件是唯一可靠通道。
 */
function fire(key: string) {
  if (!node) return;
  node.model?.graph?.trigger('start:menu', { node, key, label: key });
}
</script>

<template>
  <div
    class="loop-group"
    :class="{ 'is-dimmed': dimmed }"
    :style="{ borderColor }"
  >
    <!-- 框头 bar：唯一可交互区（框体 pointer-events:none 让子节点透出） -->
    <div class="loop-head" :style="{ background: borderColor }">
      <span class="loop-icon">FOR</span>
      <span class="loop-title" :title="label">{{ label }}</span>
      <span v-if="summary" class="loop-summary">{{ summary }}</span>
      <span class="loop-ops">
        <span class="loop-op" title="配置循环" @click.stop="fire('loop-config')">⚙</span>
        <span class="loop-op" title="解散循环" @click.stop="fire('loop-dissolve')">✕</span>
      </span>
    </div>
  </div>
</template>

<style scoped>
/* 容器：虚线大框，框体透明（不挡内部子节点） */
.loop-group {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  pointer-events: none; /* 框体放行，事件交给内部子节点 */
  background: rgb(95 149 255 / 4%);
  border: 1.5px dashed #5f95ff;
  border-radius: 12px;
}

.loop-group.is-dimmed {
  opacity: 0.5;
}

/* 框头 bar：恢复 pointer-events，承接配置/解散点击 */
.loop-head {
  position: absolute;
  top: -14px;
  left: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  pointer-events: auto;
  font-family: inter, 'PingFang SC', arial, sans-serif;
  color: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 15%);
}

.loop-icon {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.loop-title {
  max-width: 140px;
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loop-summary {
  padding: 0 6px;
  font-size: 11px;
  background: rgb(255 255 255 / 22%);
  border-radius: 8px;
}

.loop-ops {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-left: 4px;
}

.loop-op {
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  opacity: 0.85;
  transition: opacity 0.15s;
}

.loop-op:hover {
  opacity: 1;
}
</style>
