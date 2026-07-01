<script lang="ts" setup>
import { ElButton, ElDivider, ElInput, ElTooltip } from 'element-plus';

import ProfileSelector from './selectors/ProfileSelector.vue';

defineOptions({ name: 'FlowToolbar' });

defineProps<Props>();

const emit = defineEmits<{
  back: [];
  redo: [];
  run: [];
  save: [];
  undo: [];
}>();

interface Props {
  isEdit: boolean;
  saving: boolean;
  canUndo: boolean;
  canRedo: boolean;
}

/** 元信息字段各自双向绑定，避免直接 mutate 父级 prop */
const flowCode = defineModel<string>('flowCode', { default: '' });
const name = defineModel<string>('name', { default: '' });
const defaultProfileCode = defineModel<string>('defaultProfileCode', {
  default: '',
});
</script>

<template>
  <div
    class="flex flex-wrap items-center gap-2 border-b bg-white px-4 py-2 dark:bg-[#1d1e1f]"
  >
    <ElInput
      v-model="flowCode"
      :disabled="isEdit"
      class="!w-44"
      placeholder="流程编码"
      size="small"
    />
    <ElInput
      v-model="name"
      class="!w-44"
      placeholder="流程名称"
      size="small"
    />
    <ProfileSelector
      v-model="defaultProfileCode"
      class="!w-56"
      placeholder="默认模型档案"
      size="small"
    />

    <ElDivider direction="vertical" />

    <ElTooltip content="撤销 (Ctrl+Z)" placement="bottom">
      <ElButton :disabled="!canUndo" size="small" @click="emit('undo')">
        撤销
      </ElButton>
    </ElTooltip>
    <ElTooltip content="重做 (Ctrl+Shift+Z)" placement="bottom">
      <ElButton :disabled="!canRedo" size="small" @click="emit('redo')">
        重做
      </ElButton>
    </ElTooltip>

    <div class="flex-1"></div>

    <ElButton size="small" @click="emit('back')">返回</ElButton>
    <ElButton size="small" @click="emit('run')">运行</ElButton>
    <ElButton
      :loading="saving"
      size="small"
      type="primary"
      @click="emit('save')"
    >
      保存
    </ElButton>
  </div>
</template>
