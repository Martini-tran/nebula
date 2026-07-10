<script lang="ts" setup>
/**
 * JSON 编辑字段：包一层 @idss-d/json-editor-vue3（Monaco 内核），
 * 取代散落在各配置组件里的 <ElInput type="textarea"> + 手写格式化按钮。
 *
 * 对外仍是 v-model 一个 JSON 字符串——与原 textarea 契约一致，
 * 各处 handleConfirm 里的 JSON.parse 校验逻辑无需改动。
 *
 * 内置能力（原来每处都要手写一遍）：
 * - 语法高亮 + 括号匹配 + 折叠 + monaco 语言服务的实时诊断（波浪线）
 * - 右上角格式化按钮与全屏按钮
 * - 语法错误冒泡为下方红字，非法时不阻断输入
 * - 跟随应用暗色主题
 *
 * ⚠️ 底层 JsonEditor 的两个坑，本组件负责兜住：
 *
 * 1. 它只在「失焦 && JSON 合法」时才 emit update:modelValue
 *    （handleEditorBlur 里 `if (validate()) handleEditorChange()`）。
 *    → 输入后不失焦直接点「应用」会拿到旧值；JSON 写到一半更是永不回写。
 *    本组件低频轮询 expose 的 getValue() 主动读回（它没 expose monaco 实例，
 *    无法监听 onDidChangeModelContent）。
 *
 * 2. 它 watch modelValue 时会 `JSON.parse(val)`，失败就 **静默重置成 {}**，
 *    成功也会 `JSON.stringify(v, null, 2)` 强制重排版。
 *    → 把 v-model 回灌给它，用户写到一半的 JSON 会被清空。
 *    故 modelValue 只在挂载/hydrate 时喂一次：外部值变化时递增 editorKey
 *    强制重建编辑器，而不是依赖它的 prop watch。
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';

import { usePreferences } from '@nebula/preferences';

import JsonEditor from '@idss-d/json-editor-vue3';

// 样式子路径是 exports 里登记的 './JsonEditor.css'（不是 './dist/JsonEditor.css'）
import '@idss-d/json-editor-vue3/JsonEditor.css';

defineOptions({ name: 'JsonField' });

withDefaults(
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
const theme = computed<'dark' | 'light'>(() => (isDark.value ? 'dark' : 'light'));

/** 编辑器实时反馈的语法错误（null 表示合法/为空） */
const error = ref<null | string>(null);

interface EditorExpose {
  format: () => void;
  getValue: () => object | string;
  validate: () => boolean;
}
const editorRef = ref<EditorExpose>();

/**
 * 喂给编辑器的初始值 + 重建键。
 * 只有外部 hydrate（打开面板载入草稿、切换节点）才 bump；用户敲字不会。
 */
const seed = ref(model.value ?? '');
const editorKey = ref(0);

/** 编辑器当前文本（对象形态统一 stringify，保证 v-model 类型稳定） */
function readEditor(): string {
  const v = editorRef.value?.getValue();
  if (v === undefined || v === null) return '';
  return typeof v === 'string' ? v : JSON.stringify(v, null, 2);
}

/**
 * 轮询把编辑器内容同步回 model。
 *
 * 不用 DOM 事件：monaco 的输入走隐藏 textarea + 自有命令体系，keyup/input
 * 覆盖不全（粘贴、右键菜单、快捷键、格式化都不触发），实测漏事件。
 * getValue() 是同步准确的，低频轮询最省心，开销是每 200ms 读一个字符串。
 */
const POLL_MS = 200;
let timer: null | ReturnType<typeof setInterval> = null;

function syncFromEditor() {
  if (!editorRef.value) return;
  const next = readEditor();
  if (next !== model.value) model.value = next;
}

watch(
  editorRef,
  (inst) => {
    if (timer) clearInterval(timer);
    timer = inst ? setInterval(syncFromEditor, POLL_MS) : null;
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});

/**
 * 外部 hydrate：重建编辑器喂新初始值。
 *
 * 不能把新值直接回灌给它的 modelValue——底层 watch 里 `JSON.parse(val)` 失败会
 * 静默重置成 {}，成功也会强制重排版；换 key 重建是唯一干净的喂值方式。
 *
 * 靠「新值是否等于编辑器当前内容」区分来源：相等说明是上面轮询回写的（用户输入），
 * 编辑器已经是这个内容，不必动；不等才是外部塞进来的新值，需要重建。
 * flush:'sync' 必需——默认的异步批处理会让回调延后到下一个 tick，期间用户可能
 * 又敲了字，readEditor() 与 next 不再相等，就会把用户输入误判成 hydrate 而重建。
 */
watch(
  model,
  (next) => {
    const text = next ?? '';
    if (editorRef.value && text === readEditor()) return;
    seed.value = text;
    editorKey.value += 1;
  },
  { flush: 'sync' },
);

/** 供调用方主动触发（多数地方仍在 handleConfirm 里用自己的 JSON.parse 校验） */
defineExpose({
  format: () => editorRef.value?.format(),
  validate: () => editorRef.value?.validate() ?? true,
});
</script>

<template>
  <div class="json-field">
    <JsonEditor
      :key="editorKey"
      ref="editorRef"
      :height="height"
      :model-value="seed"
      :placeholder="placeholder"
      :readonly="readonly"
      :show-format-button="!readonly"
      :show-fullscreen-button="fullscreen"
      :theme="theme"
      @update:error="error = $event"
    />
    <div v-if="error" class="json-field-error">{{ error }}</div>
  </div>
</template>

<style scoped>
.json-field {
  width: 100%;
}

.json-field-error {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-color-danger);
}
</style>
