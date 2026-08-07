<script lang="ts" setup>
/**
 * 开始节点入参编辑器（JSON 文本编辑）。
 *
 * 入参是一个 StartInputParam[] 数组，直接以 JSON 呈现/编辑，底层用
 * vanilla-jsoneditor（框架无关，命令式 API）：自带文本/树双模式、语法高亮、
 * 实时格式校验与错误定位。
 *
 * 与外部的契约不变——v-model 仍是 StartInputParam[]，因此画布卡片摘要
 * (StartNodeCard) 与属性面板 (PropertyPanel) 无需感知底层换成了 JSON 编辑：
 *   - 挂载/回填：把数组 stringify 成 JSON 文本喂给编辑器；
 *   - 编辑同步：编辑器内容变更时尝试解析，仅当解析成功且为数组才 emit，
 *     解析失败保留中间态（编辑器自身红标提示），不写回脏数据。
 *
 * 单条入参字段：name/key/type/required/defaultValue/description/example/validation。
 * validation 按 type 取用子集（String:长度/正则/枚举；Number:范围/枚举；
 * File/Image:大小/格式；Boolean/Object/Array 无校验）。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { createJSONEditor } from 'vanilla-jsoneditor';

import 'vanilla-jsoneditor/themes/jse-theme-default.css';

defineOptions({ name: 'StartInputsEditor' });

/** 入参类型枚举 */
export type StartInputType =
  | 'Array'
  | 'Boolean'
  | 'File'
  | 'Image'
  | 'Number'
  | 'Object'
  | 'String';

/** 校验规则（按类型取用其中子集，未用到的键保持缺省） */
export interface StartInputValidation {
  /** String：最小/最大长度 */
  minLength?: null | number;
  maxLength?: null | number;
  /** String：正则 */
  pattern?: string;
  /** Number：最小/最大值 */
  min?: null | number;
  max?: null | number;
  /** String/Number：枚举（逗号分隔的候选值） */
  enumValues?: string;
  /** File/Image：大小上限（MB） */
  maxSizeMb?: null | number;
  /** File/Image：允许格式（逗号分隔，如 png,jpg） */
  accept?: string;
}

/** 单条入参定义 */
export interface StartInputParam {
  name?: string;
  key?: string;
  type?: StartInputType;
  required?: boolean;
  defaultValue?: string;
  description?: string;
  example?: string;
  validation?: StartInputValidation;
}

const model = defineModel<StartInputParam[]>({ default: () => [] });

/** 新增入参时插入的模板（点击「插入示例」用） */
const SAMPLE: StartInputParam[] = [
  {
    name: '用户问题',
    key: 'question',
    type: 'String',
    required: true,
    description: '用户输入的原始问题',
    example: '帮我写一首诗',
    validation: { maxLength: 500 },
  },
];

const containerRef = ref<HTMLElement>();
let editor: null | ReturnType<typeof createJSONEditor> = null;

/** 标记「更新来自编辑器自身」，避免 watch 回写导致光标/编辑态抖动 */
let updatingFromEditor = false;

/** 把当前 model 序列化成编辑器可接受的 content */
function toContent() {
  return { json: model.value ?? [] };
}

/** 编辑器内容变更回调：仅当能解析为数组时才写回 model */
function onEditorChange(updatedContent: any) {
  let next: unknown;
  try {
    // text 模式下 content.text 是字符串；tree 模式下 content.json 是对象
    next =
      updatedContent.json === undefined
        ? JSON.parse(updatedContent.text)
        : updatedContent.json;
  } catch {
    // 非法 JSON：编辑器已红标提示，保留中间态不写回
    return;
  }
  if (!Array.isArray(next)) return;
  updatingFromEditor = true;
  model.value = next as StartInputParam[];
}

/** 插入示例（覆盖当前空内容或追加） */
function insertSample() {
  const base = Array.isArray(model.value) ? model.value : [];
  const next = [...base, ...SAMPLE];
  model.value = next;
  editor?.update(toContent());
}

onMounted(() => {
  if (!containerRef.value) return;
  editor = createJSONEditor({
    target: containerRef.value,
    props: {
      content: toContent(),
      mode: 'text' as any,
      mainMenuBar: true,
      navigationBar: false,
      statusBar: true,
      onChange: onEditorChange,
    },
  });
});

onBeforeUnmount(() => {
  editor?.destroy();
  editor = null;
});

/** 外部（如切换选中节点）改动 model 时，同步进编辑器；跳过编辑器自身触发的回写 */
watch(
  model,
  () => {
    if (updatingFromEditor) {
      updatingFromEditor = false;
      return;
    }
    editor?.update(toContent());
  },
  { deep: true },
);
</script>

<template>
  <div class="inputs-json-editor">
    <div class="toolbar">
      <span class="hint">入参为 JSON 数组，每项定义一个参数</span>
      <a class="sample-link" @click="insertSample">插入示例</a>
    </div>
    <div ref="containerRef" class="editor-host"></div>
  </div>
</template>

<style scoped>
.inputs-json-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sample-link {
  color: var(--el-color-primary);
  cursor: pointer;
}

.sample-link:hover {
  text-decoration: underline;
}

/* 编辑器高度受控，随内容滚动 */
.editor-host {
  height: 320px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
</style>
