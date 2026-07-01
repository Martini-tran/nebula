<script lang="ts" setup>
/**
 * 左侧节点面板（自实现，替代官方 Stencil：3.x 官方 stencil 包已损坏无 JS）。
 *
 * 按分组（流程 / 业务逻辑 / 知识库&数据）列出全部节点类型，支持折叠。
 * 拖拽或双击把对应 nodeType 加入画布（dragstart 写 text/plain=nodeType，
 * 画布 drop 侧读取创建节点）。
 */
import type { AgentTypeMeta, NodeGroup } from '../constants';

import { computed, ref } from 'vue';

import { AGENT_NODE_TYPES, NODE_GROUP_TITLES, THEME_COLORS } from '../constants';

defineOptions({ name: 'NodePalette' });

const emit = defineEmits<{
  add: [type: string];
}>();

const GROUP_ORDER: NodeGroup[] = ['flow', 'biz', 'data'];

const grouped = computed(() =>
  GROUP_ORDER.map((group) => ({
    group,
    title: NODE_GROUP_TITLES[group],
    items: AGENT_NODE_TYPES.filter((t) => t.group === group),
  })).filter((g) => g.items.length > 0),
);

/** 折叠态：默认全部展开 */
const collapsed = ref<Record<string, boolean>>({});
function toggle(group: string) {
  collapsed.value[group] = !collapsed.value[group];
}

function onDragStart(event: DragEvent, item: AgentTypeMeta) {
  event.dataTransfer?.setData('text/plain', item.nodeType);
}
</script>

<template>
  <div class="flow-palette">
    <div class="palette-body">
      <div v-for="g in grouped" :key="g.group" class="group">
        <div class="group-title" @click="toggle(g.group)">
          <span class="chevron" :class="{ folded: collapsed[g.group] }">▾</span>
          {{ g.title }}
        </div>
        <div v-show="!collapsed[g.group]" class="group-items">
          <div
            v-for="item in g.items"
            :key="item.nodeType"
            class="node-item"
            :style="{ borderColor: THEME_COLORS[item.theme].border }"
            draggable="true"
            :title="item.desc"
            @dragstart="onDragStart($event, item)"
            @dblclick="emit('add', item.nodeType)"
          >
            <span
              class="node-icon"
              :style="{
                background: THEME_COLORS[item.theme].iconBg,
                color: THEME_COLORS[item.theme].iconColor,
              }"
            >
              {{ item.iconText }}
            </span>
            <div class="node-meta">
              <div class="node-name">
                {{ item.title }}
                <span v-if="!item.runnable" class="todo-dot" title="后端未就绪">
                  ·
                </span>
              </div>
              <div class="node-desc">{{ item.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.flow-palette {
  display: flex;
  flex-direction: column;
  width: 264px;
  height: 100%;
  min-height: 0;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.palette-body {
  flex: 1;
  min-height: 0;
  padding: 10px;
  overflow-y: auto;
}

.group {
  margin-bottom: 10px;
}

.group-title {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 6px;
  margin-bottom: 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  letter-spacing: 0.3px;
  cursor: pointer;
  user-select: none;
}

.chevron {
  font-size: 10px;
  transition: transform 0.15s;
}

.chevron.folded {
  transform: rotate(-90deg);
}

.group-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 2px 0;
}

.node-item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 11px;
  cursor: grab;
  background: var(--el-bg-color);
  border: 1px solid;
  border-radius: 8px;
  transition:
    box-shadow 0.15s,
    transform 0.05s;
}

.node-item:hover {
  box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
}

.node-item:active {
  transform: scale(0.98);
}

.node-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 8px;
}

.node-meta {
  min-width: 0;
  flex: 1;
}

.node-name {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}

.todo-dot {
  color: #fa8c16;
}

.node-desc {
  margin-top: 1px;
  overflow: hidden;
  font-size: 11px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.palette-tips {
  padding: 10px 16px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
  border-top: 1px solid var(--el-border-color-light);
}
</style>
