<script lang="ts" setup>
import { ElButton, ElDivider, ElTooltip } from 'element-plus';

defineOptions({ name: 'FlowToolbar' });

defineProps<Props>();

const emit = defineEmits<{
  back: [];
  editMeta: [];
  redo: [];
  run: [];
  save: [];
  undo: [];
}>();

interface Props {
  /** 流程名称（只读展示，编辑走基础信息抽屉） */
  name: string;
  /** 流程编码（只读展示） */
  flowCode: string;
  isEdit: boolean;
  saving: boolean;
  canUndo: boolean;
  canRedo: boolean;
}
</script>

<template>
  <div
    class="flex items-center gap-3 border-b bg-white px-4 py-2 dark:bg-[#1d1e1f]"
  >
    <!-- 左：只读标题区 -->
    <div class="flex min-w-0 items-center gap-2">
      <ElButton size="small" @click="emit('back')">返回</ElButton>
      <ElDivider direction="vertical" />
      <div class="min-w-0">
        <div class="truncate text-sm font-medium leading-tight">
          {{ name || flowCode || '未命名流程' }}
        </div>
        <div class="truncate text-xs leading-tight text-gray-400">
          {{ flowCode }}
        </div>
      </div>
    </div>

    <div class="flex-1"></div>

    <!-- 中：编辑操作 -->
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

    <ElDivider direction="vertical" />

    <!-- 右：主操作 -->
    <ElButton size="small" @click="emit('editMeta')">基础信息</ElButton>
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
