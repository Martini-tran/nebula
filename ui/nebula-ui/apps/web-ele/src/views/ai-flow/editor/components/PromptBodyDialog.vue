<script lang="ts" setup>
/**
 * 提示词正文编辑弹窗：把一段长正文的编辑从内联表单里抽到独立弹窗。
 *
 * 用草稿模式——打开时把外部值拷进本地 draft，确定才 emit 回填，取消丢弃，
 * 避免编辑途中污染外部 v-model。由 LlmConfigDialog 的系统/用户提示词各实例化
 * 一个（各自独立 visible + 各自绑定字段）。
 */
import { ref, watch } from 'vue';

import { ElButton, ElDialog, ElInput } from 'element-plus';

defineOptions({ name: 'PromptBodyDialog' });

const props = withDefaults(
  defineProps<{
    /** 正文值（v-model） */
    modelValue?: string;
    /** 弹窗标题 */
    title?: string;
    /** textarea 占位符 */
    placeholder?: string;
    /** textarea 行数 */
    rows?: number;
  }>(),
  { modelValue: '', title: '编辑正文', placeholder: '', rows: 14 },
);

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

/** 弹窗可见性 */
const visible = ref(false);
/** 本地草稿：打开时从 modelValue 拷入，确定才回写 */
const draft = ref('');

/** 打开时同步草稿（供父组件 ref 调用） */
function open() {
  draft.value = props.modelValue ?? '';
  visible.value = true;
}

/** 确定：回写并关闭 */
function handleConfirm() {
  emit('update:modelValue', draft.value);
  visible.value = false;
}

// 外部值在弹窗打开期间变化（如选中提示词带出正文），同步进草稿
watch(
  () => props.modelValue,
  (val) => {
    if (visible.value) draft.value = val ?? '';
  },
);

defineExpose({ open });
</script>

<template>
  <ElDialog
    v-model="visible"
    append-to-body
    :close-on-click-modal="false"
    :title="title"
    width="720px"
  >
    <ElInput
      v-model="draft"
      :autosize="{ minRows: rows, maxRows: 30 }"
      :placeholder="placeholder"
      type="textarea"
    />
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">保存正文</ElButton>
    </template>
  </ElDialog>
</template>
