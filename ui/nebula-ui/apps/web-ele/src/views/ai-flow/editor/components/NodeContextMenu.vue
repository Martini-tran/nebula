<script lang="ts" setup>
/**
 * 画布节点右键菜单（轻量自绘，样式对齐 element-plus 下拉菜单）。
 *
 * 之所以不用 @nebula-core 的 nebulaContextMenu：那套是 shadcn 视觉体系，
 * 与编辑器的 element-plus 风格不统一；且 X6 画布需要按鼠标坐标程序化弹出，
 * 触发区包裹式的组件并不贴合。本组件 teleport 到 body、fixed 定位于
 * 鼠标点，视口边缘自动内收，点击外部 / Escape / 滚轮 / 窗口变化即关闭。
 *
 * 用法：父组件持 ref，右键事件里调 open(e.clientX, e.clientY)，
 * 监听 select 事件拿被点项的 key。菜单项由 items prop 声明。
 */
import { nextTick, onBeforeUnmount, ref } from 'vue';

defineOptions({ name: 'NodeContextMenu' });

const props = defineProps<{ items: NodeMenuItem[] }>();

const emit = defineEmits<{ select: [key: string] }>();

/** 单个菜单项 */
export interface NodeMenuItem {
  key: string;
  label: string;
  /** 置灰不可点 */
  disabled?: boolean;
  /** 该项上方加分隔线 */
  divided?: boolean;
}

const visible = ref(false);
const menuRef = ref<HTMLDivElement>();
const pos = ref({ x: 0, y: 0 });

/** 在鼠标点弹出，渲染后按菜单实际尺寸做视口边缘内收 */
async function open(x: number, y: number) {
  pos.value = { x, y };
  visible.value = true;
  await nextTick();
  const el = menuRef.value;
  if (!el) return;
  const { innerHeight, innerWidth } = window;
  const rect = el.getBoundingClientRect();
  pos.value = {
    x: Math.min(x, Math.max(0, innerWidth - rect.width - 8)),
    y: Math.min(y, Math.max(0, innerHeight - rect.height - 8)),
  };
  bindDismiss();
}

function close() {
  if (!visible.value) return;
  visible.value = false;
  unbindDismiss();
}

function onItemClick(item: NodeMenuItem) {
  if (item.disabled) return;
  close();
  emit('select', item.key);
}

// ---------------- 关闭时机：点外部 / 再次右键 / Escape / 滚轮 / 窗口变化 ----------------
function onDocPointerDown(e: MouseEvent) {
  if (menuRef.value?.contains(e.target as Node)) return;
  close();
}
function onDocKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') close();
}

function bindDismiss() {
  document.addEventListener('mousedown', onDocPointerDown, true);
  document.addEventListener('contextmenu', onDocPointerDown, true);
  document.addEventListener('keydown', onDocKeydown, true);
  window.addEventListener('wheel', close, { capture: true, passive: true });
  window.addEventListener('resize', close);
  window.addEventListener('blur', close);
}
function unbindDismiss() {
  document.removeEventListener('mousedown', onDocPointerDown, true);
  document.removeEventListener('contextmenu', onDocPointerDown, true);
  document.removeEventListener('keydown', onDocKeydown, true);
  window.removeEventListener('wheel', close, true);
  window.removeEventListener('resize', close);
  window.removeEventListener('blur', close);
}

onBeforeUnmount(unbindDismiss);

defineExpose({ close, open });
</script>

<template>
  <Teleport to="body">
    <Transition name="el-zoom-in-top">
      <div
        v-if="visible"
        ref="menuRef"
        class="node-context-menu"
        :style="{ left: `${pos.x}px`, top: `${pos.y}px` }"
        @contextmenu.prevent
      >
        <template v-for="item in props.items" :key="item.key">
          <div v-if="item.divided" class="menu-divider"></div>
          <div
            class="menu-item"
            :class="{ 'is-disabled': item.disabled }"
            @click="onItemClick(item)"
          >
            {{ item.label }}
          </div>
        </template>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* 对齐 element-plus 下拉菜单视觉：overlay 背景 + 细边框 + 轻阴影 + 主色 hover */
.node-context-menu {
  position: fixed;
  z-index: 3000;
  min-width: 148px;
  padding: 5px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-light);
}

.menu-item {
  padding: 7px 12px;
  font-size: 13px;
  line-height: 1.4;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  cursor: pointer;
  user-select: none;
  border-radius: 5px;
  transition:
    background-color 0.15s,
    color 0.15s;
}

.menu-item:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.menu-item.is-disabled {
  color: var(--el-text-color-disabled);
  cursor: not-allowed;
}

.menu-item.is-disabled:hover {
  color: var(--el-text-color-disabled);
  background: transparent;
}

.menu-divider {
  margin: 4px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
