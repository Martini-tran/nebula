<script lang="ts" setup>
// 复刻 EditorSideDock 的 state + 模板（面板内容用占位），外层套真实 index.vue 高度链路。
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

type PanelKey = 'config' | 'ai';
type Arrangement = 'split' | 'tab';
interface PanelDef { key: PanelKey; title: string; icon: string; tooltip: string }
const PANELS: PanelDef[] = [
  { key: 'config', title: '配置', icon: '⚙', tooltip: '节点 / 连线配置' },
  { key: 'ai', title: 'AI 生成', icon: '✨', tooltip: 'AI 对话辅助编排' },
];
const LAYOUT_KEY = 'ai-flow:editor-sidedock-probe';
const MIN_WIDTH = 280, MAX_WIDTH = 720, DEFAULT_WIDTH = 360;
interface DockState { open: PanelKey[]; arrangement: Arrangement; activeTab: PanelKey; width: number }
const state = reactive<DockState>({ open: [], arrangement: 'tab', activeTab: 'config', width: DEFAULT_WIDTH });

const isOpen = (key: PanelKey) => state.open.includes(key);
const bothOpen = computed(() => state.open.length === 2);
const panelAreaVisible = computed(() => state.open.length > 0);
function paneVisible(key: PanelKey): boolean {
  if (!isOpen(key)) return false;
  if (state.arrangement === 'split' && bothOpen.value) return true;
  return state.activeTab === key;
}
function openPanel(key: PanelKey) { if (!isOpen(key)) state.open.push(key); state.activeTab = key; }
function closePanel(key: PanelKey) {
  const idx = state.open.indexOf(key);
  if (idx !== -1) state.open.splice(idx, 1);
  if (state.activeTab === key && state.open.length > 0) state.activeTab = state.open[0]!;
}
function togglePanel(key: PanelKey) {
  if (isOpen(key)) {
    if (state.arrangement === 'split' && bothOpen.value) closePanel(key);
    else if (state.activeTab === key) closePanel(key);
    else state.activeTab = key;
  } else openPanel(key);
}
function toggleArrangement() { state.arrangement = state.arrangement === 'tab' ? 'split' : 'tab'; }
function panelTitle(key: PanelKey): string { return PANELS.find((p) => p.key === key)?.title ?? ''; }

const resizing = ref(false);
let startX = 0, startWidth = 0;
function onResizeStart(e: MouseEvent) {
  resizing.value = true; startX = e.clientX; startWidth = state.width;
  window.addEventListener('mousemove', onResizeMove); window.addEventListener('mouseup', onResizeEnd); e.preventDefault();
}
function onResizeMove(e: MouseEvent) { state.width = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth + (startX - e.clientX))); }
function onResizeEnd() { resizing.value = false; window.removeEventListener('mousemove', onResizeMove); window.removeEventListener('mouseup', onResizeEnd); }
function persist() { try { localStorage.setItem(LAYOUT_KEY, JSON.stringify(state)); } catch { /* noop */ } }
onMounted(() => { try { const r = localStorage.getItem(LAYOUT_KEY); if (r) Object.assign(state, JSON.parse(r)); } catch { /* noop */ } });
watch(() => [state.open.slice(), state.arrangement, state.activeTab, state.width], persist, { deep: true });
onBeforeUnmount(onResizeEnd);

// 暴露给探针
(window as any).__dock = { openPanel, closePanel, togglePanel, toggleArrangement, state };
</script>

<template>
  <div style="position:absolute;inset:0;display:flex;flex-direction:column;">
    <div style="height:48px;flex:0 0 auto;background:#eee;line-height:48px;padding:0 12px;">工具栏</div>
    <div style="display:flex;min-height:0;flex:1;">
      <div style="width:120px;flex:0 0 auto;background:#f5f5f5;">节点面板</div>
      <div class="editor-dock" :class="{ 'is-resizing': resizing }">
        <div class="dock-center">
          <div style="position:relative;height:100%;width:100%;background:#eaf4ff;border:2px solid #bcd;box-sizing:border-box;">画布（centerArea）</div>
        </div>
        <div v-show="panelAreaVisible" class="dock-panels" :style="{ width: `${state.width}px` }">
          <div class="dock-resizer" @mousedown="onResizeStart"></div>
          <div class="dock-header">
            <div class="dock-tabs">
              <template v-if="state.arrangement === 'tab' || !bothOpen">
                <button v-for="key in state.open" :key="key" class="dock-tab" :class="{ active: state.activeTab === key }" @click="state.activeTab = key">{{ panelTitle(key) }}</button>
              </template>
              <span v-else class="dock-split-title">上下分屏</span>
            </div>
            <div class="dock-header-actions">
              <button v-if="bothOpen" class="dock-icon-btn" :title="state.arrangement === 'tab' ? '上下分屏' : '标签模式'" @click="toggleArrangement">{{ state.arrangement === 'tab' ? '⬍' : '⬒' }}</button>
              <button class="dock-icon-btn" title="关闭当前面板" @click="closePanel(state.arrangement === 'split' && bothOpen ? state.open[0]! : state.activeTab)">✕</button>
            </div>
          </div>
          <div class="dock-body" :class="`arrange-${bothOpen ? state.arrangement : 'single'}`">
            <div v-show="paneVisible('config')" class="dock-pane">
              <div v-if="state.arrangement === 'split' && bothOpen" class="dock-pane-title">配置<button class="dock-icon-btn" @click="closePanel('config')">✕</button></div>
              <div class="dock-pane-content" style="padding:12px;">配置面板占位</div>
            </div>
            <div v-show="paneVisible('ai')" class="dock-pane">
              <div v-if="state.arrangement === 'split' && bothOpen" class="dock-pane-title">AI 生成<button class="dock-icon-btn" @click="closePanel('ai')">✕</button></div>
              <div class="dock-pane-content" style="padding:12px;">AI 面板占位</div>
            </div>
          </div>
        </div>
        <div class="dock-activity">
          <button v-for="p in PANELS" :key="p.key" class="dock-activity-item" :class="{ active: isOpen(p.key) }" :title="p.tooltip" @click="togglePanel(p.key)">
            <span class="dock-activity-icon">{{ p.icon }}</span>
            <span class="dock-activity-label">{{ p.title }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.editor-dock { position: relative; display: flex; flex: 1; min-width: 0; min-height: 0; overflow: hidden; }
.editor-dock.is-resizing { cursor: ew-resize; user-select: none; }
.dock-center { position: relative; flex: 1; min-width: 0; min-height: 0; }
.dock-activity { display: flex; flex: 0 0 auto; flex-direction: column; width: 52px; border-left: 1px solid var(--el-border-color, #dcdfe6); background: var(--el-bg-color-page, #f5f7fa); }
.dock-activity-item { display: flex; flex-direction: column; align-items: center; gap: 2px; width: 100%; padding: 8px 2px; border: none; border-left: 2px solid transparent; background: transparent; color: var(--el-text-color-regular, #606266); font-size: 11px; line-height: 1.2; cursor: pointer; }
.dock-activity-item:hover { background: var(--el-fill-color-light, #f2f6fc); color: var(--el-text-color-primary, #303133); }
.dock-activity-item.active { border-left-color: var(--el-color-primary, #409eff); color: var(--el-color-primary, #409eff); background: var(--el-color-primary-light-9, #ecf5ff); }
.dock-activity-icon { font-size: 18px; }
.dock-activity-label { transform: scale(0.92); white-space: nowrap; }
.dock-panels { position: relative; display: flex; flex: 0 0 auto; flex-direction: column; min-width: 0; border-left: 1px solid var(--el-border-color, #dcdfe6); background: var(--el-bg-color, #fff); }
.dock-resizer { position: absolute; top: 0; left: -3px; z-index: 5; width: 6px; height: 100%; cursor: ew-resize; background: transparent; }
.dock-resizer:hover { background: var(--el-color-primary-light-7, #a0cfff); }
.dock-header { display: flex; flex: 0 0 auto; align-items: center; justify-content: space-between; height: 38px; padding: 0 4px 0 8px; border-bottom: 1px solid var(--el-border-color-light, #e4e7ed); background: var(--el-bg-color-page, #f5f7fa); }
.dock-tabs { display: flex; min-width: 0; height: 100%; overflow: hidden; }
.dock-tab { height: 100%; padding: 0 12px; border: none; border-bottom: 2px solid transparent; background: transparent; color: var(--el-text-color-regular, #606266); font-size: 13px; cursor: pointer; }
.dock-tab:hover { color: var(--el-text-color-primary, #303133); }
.dock-tab.active { color: var(--el-color-primary, #409eff); border-bottom-color: var(--el-color-primary, #409eff); }
.dock-split-title { display: flex; align-items: center; padding: 0 8px; color: var(--el-text-color-secondary, #909399); font-size: 12px; }
.dock-header-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 2px; }
.dock-icon-btn { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; padding: 0; border: none; border-radius: 4px; background: transparent; color: var(--el-text-color-secondary, #909399); font-size: 13px; cursor: pointer; }
.dock-icon-btn:hover { background: var(--el-fill-color, #f0f2f5); color: var(--el-text-color-primary, #303133); }
.dock-body { display: flex; flex: 1; min-height: 0; }
.dock-body.arrange-split { flex-direction: column; }
.dock-body.arrange-tab, .dock-body.arrange-single { flex-direction: row; }
.dock-pane { display: flex; flex: 1; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; }
.dock-body.arrange-split > .dock-pane + .dock-pane { border-top: 1px solid var(--el-border-color-light, #e4e7ed); }
.dock-pane-title { display: flex; flex: 0 0 auto; align-items: center; justify-content: space-between; height: 30px; padding: 0 4px 0 10px; background: var(--el-fill-color-lighter, #fafafa); color: var(--el-text-color-regular, #606266); font-size: 12px; }
.dock-pane-content { flex: 1; min-height: 0; overflow: auto; }
</style>
