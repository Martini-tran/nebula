<script lang="ts" setup>
/**
 * JSON 编辑字段：直接驱动 Monaco，取代散落在各配置组件里的
 * <ElInput type="textarea"> + 手写格式化按钮。
 *
 * 对外是 v-model 一个 JSON 字符串——与原 textarea 契约一致，
 * 各处 handleConfirm 里的 JSON.parse 校验逻辑无需改动。
 *
 * 能力：
 * - 语法高亮 + 括号匹配 + 折叠 + JSON 语言服务的实时诊断（波浪线）
 * - 右上角格式化按钮与全屏按钮
 * - 语法错误冒泡为下方红字，非法时不阻断输入
 * - 跟随应用暗色主题
 *
 * ⚠️ 这里曾包着 @idss-d/json-editor-vue3，因它有三处硬伤而移除：
 * 1. 它的 CSS 用 `[style*="1.67772e+07px"] { height:100%!important;
 *    transform:none!important }` 直接篡改 monaco 内部 DOM：实测 .lines-content
 *    的 translate3d 被抹成 none、高度被从 2^24px 改成 100%，monaco 只能退回用
 *    `top` 兜底滚动，且 overflow-guard 的高度与外层容器对不上。
 * 2. getValue() 对合法 JSON 返回的是 parse 后的**对象**而非原始文本，
 *    回读再 stringify 会悄悄重排版、吃掉用户的缩进与键序。
 * 3. 它只在「失焦 && JSON 合法」时才 emit，逼得外层用 200ms 轮询回读、
 *    再靠强制重建编辑器来喂值——重建会丢光标与滚动位置。
 *
 * 直接用 monaco 后：内部 DOM 不再被外部 CSS 改写，getValue() 恒为原始文本，
 * onDidChangeModelContent 实时且精确，轮询与强制重建全部消失。
 */
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';

import { AlignLeft, Fullscreen, Minimize } from '@nebula/icons';
import { usePreferences } from '@nebula/preferences';

import { ElButton, ElTooltip } from 'element-plus';

import * as monaco from 'monaco-editor';

defineOptions({ name: 'JsonField' });

const props = withDefaults(
  defineProps<{
    /** 编辑器高度（px） */
    height?: number;
    placeholder?: string;
    readonly?: boolean;
    /** 是否显示全屏按钮：内容长的（Schema、参数模板）建议开 */
    fullscreen?: boolean;
  }>(),
  {
    height: 200,
    placeholder: '',
    readonly: false,
    fullscreen: true,
  },
);

/** JSON 文本（与原 textarea 一致：始终是字符串，空串表示未填） */
const model = defineModel<string>({ default: '' });

const { isDark } = usePreferences();

/** 编辑器实时反馈的语法错误（null 表示合法/为空） */
const error = ref<null | string>(null);
const isFullscreen = ref(false);
const isEmpty = ref(!model.value);

const container = ref<HTMLElement>();
/** monaco 实例不需要响应式代理，shallowRef 免得深层遍历它的内部结构 */
const editor = shallowRef<monaco.editor.IStandaloneCodeEditor>();

/**
 * 「这次 model 变更是编辑器自己写的」标记。
 *
 * 用户输入 → onDidChangeModelContent → 写 model → 触发下面的 watch。
 * 若不识别来源，watch 会把同一份文本再 setValue 回编辑器，光标跳到末尾。
 */
let selfWrite = false;

/** 把 monaco 的 marker（语言服务诊断）折成一行错误提示 */
function refreshError() {
  const m = editor.value?.getModel();
  if (!m) return;
  const markers = monaco.editor
    .getModelMarkers({ resource: m.uri })
    .filter((k) => k.severity === monaco.MarkerSeverity.Error);
  const first = markers[0];
  error.value = first ? `第 ${first.startLineNumber} 行：${first.message}` : null;
}

onMounted(() => {
  if (!container.value) return;

  editor.value = monaco.editor.create(container.value, {
    value: model.value ?? '',
    language: 'json',
    readOnly: props.readonly,
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    // 竖向滚动条常驻可见，避免「看起来不能滚」
    scrollbar: { vertical: 'auto', alwaysConsumeMouseWheel: false },
    lineNumbersMinChars: 3,
    folding: true,
    tabSize: 2,
    fontSize: 13,
    padding: { top: 6, bottom: 6 },
  });

  // 实时回写：getValue() 恒为编辑器里的原始文本，不做任何 parse/重排版
  editor.value.onDidChangeModelContent(() => {
    const text = editor.value!.getValue();
    isEmpty.value = !text;
    selfWrite = true;
    model.value = text;
    selfWrite = false;
  });

  // 语言服务的诊断是异步产出的，监听 marker 变化而不是自己 JSON.parse
  const dispose = monaco.editor.onDidChangeMarkers(() => refreshError());
  onBeforeUnmount(() => dispose.dispose());
});

/**
 * 外部 hydrate（打开面板载入草稿、切换节点）才写回编辑器。
 * 用 selfWrite 识别来源；再比一次文本，防止外部把同样的值塞回来时白跳光标。
 */
watch(model, (next) => {
  if (selfWrite || !editor.value) return;
  const text = next ?? '';
  if (text === editor.value.getValue()) return;
  editor.value.setValue(text);
  isEmpty.value = !text;
});

/**
 * 主题跟随应用亮/暗色。
 * setTheme 是 monaco 的全局设置（非单实例），全应用共用一套主题即可；
 * immediate 保证首帧就位，不必再在 create() 里重复传 theme。
 */
watch(isDark, (dark) => monaco.editor.setTheme(dark ? 'vs-dark' : 'vs'), {
  immediate: true,
});

watch(
  () => props.readonly,
  (ro) => editor.value?.updateOptions({ readOnly: ro }),
);

/** 全屏切换后容器尺寸变了，automaticLayout 会跟上，但立刻 layout 一次更跟手 */
function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value;
  requestAnimationFrame(() => editor.value?.layout());
}

function format() {
  editor.value?.getAction('editor.action.formatDocument')?.run();
}

onBeforeUnmount(() => {
  editor.value?.getModel()?.dispose();
  editor.value?.dispose();
});

/** 供调用方主动触发（多数地方仍在 handleConfirm 里用自己的 JSON.parse 校验） */
defineExpose({
  format,
  validate: () => error.value === null,
});
</script>

<template>
  <div class="json-field" :class="{ 'is-fullscreen': isFullscreen }">
    <div class="json-field-box" :class="{ 'is-error': !!error }">
      <div
        ref="container"
        class="json-field-editor"
        :style="{ height: isFullscreen ? '100%' : `${height}px` }"
      ></div>

      <!-- monaco 没有内置 placeholder，空内容时覆一层提示文本 -->
      <div v-if="isEmpty && placeholder" class="json-field-placeholder">
        {{ placeholder }}
      </div>

      <div v-if="!readonly || fullscreen" class="json-field-toolbar">
        <ElTooltip v-if="!readonly" content="格式化 JSON" placement="top">
          <ElButton circle size="small" @click="format">
            <AlignLeft class="json-field-icon" />
          </ElButton>
        </ElTooltip>
        <ElTooltip
          v-if="fullscreen"
          :content="isFullscreen ? '退出全屏' : '全屏编辑'"
          placement="top"
        >
          <ElButton circle size="small" @click="toggleFullscreen">
            <Minimize v-if="isFullscreen" class="json-field-icon" />
            <Fullscreen v-else class="json-field-icon" />
          </ElButton>
        </ElTooltip>
      </div>
    </div>

    <div v-if="error" class="json-field-error">{{ error }}</div>
  </div>
</template>

<style scoped>
.json-field {
  width: 100%;
}

.json-field-box {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.json-field-box.is-error {
  border-color: var(--el-color-danger);
}

.json-field-editor {
  width: 100%;
}

/* 全屏：铺满视口，编辑器高度改由 flex 撑满 */
.json-field.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 2100;
  background: var(--el-bg-color);
}

.json-field.is-fullscreen .json-field-box {
  height: 100%;
  border: none;
  border-radius: 0;
}

.json-field-placeholder {
  position: absolute;
  top: 6px;
  /* 让开行号槽，且不吃掉鼠标事件 */
  left: 52px;
  font-size: 13px;
  line-height: 19px;
  color: var(--el-text-color-placeholder);
  pointer-events: none;
  user-select: none;
}

.json-field-toolbar {
  position: absolute;
  top: 2px;
  /* 让开竖向滚动条 */
  right: 16px;
  z-index: 10;
  display: flex;
  gap: 2px;
}

.json-field-toolbar :deep(.el-button) {
  padding: 4px;
  background: transparent;
  border: none;
  box-shadow: none;
}

.json-field-icon {
  width: 15px;
  height: 15px;
  color: var(--el-text-color-secondary);
}

.json-field-error {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-danger);
}
</style>
