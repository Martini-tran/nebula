<script lang="ts" setup>
/**
 * Schema 驱动的映射编辑器：一侧字段由目标 Agent 的 IO 契约（JSON Schema）锁定，
 * 用户只填另一侧的上下文键，替代自由填写，消灭「AGENT 契约错配」。
 *
 * 两个方向（与 nodeConfig 落库结构一致，样式对齐 InputMappingEditor）：
 *   - fixedSide='key'（Input Mapping）：map 的 key=子入参键（锁定），用户填 value=父上下文键
 *   - fixedSide='value'（Output Mapping）：map 的 value=子产物键（锁定），用户填 key=父上下文键
 *
 * modelValue 始终是完整映射对象；本组件只增删改「自己字段」对应的条目，
 * schema 之外的存量条目原样保留（由外层「自定义补充」小节编辑）。
 */
import type { StartInputParam } from '../start-input';

import { ElInput, ElTooltip } from 'element-plus';

defineOptions({ name: 'SchemaMappingEditor' });

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  editablePlaceholder: '上下文键',
});

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, string>];
}>();

interface Props {
  /** 契约字段列表（schemaToStartInputs 的产物） */
  fields: StartInputParam[];
  /** 完整映射对象（含 schema 外的自定义条目，原样保留） */
  modelValue?: Record<string, string>;
  /** 锁定侧：'key'=子入参键锁定（Input），'value'=子产物键锁定（Output） */
  fixedSide: 'key' | 'value';
  editablePlaceholder?: string;
}

/** 该字段当前映射的「可编辑侧」取值 */
function editableOf(field: StartInputParam): string {
  const key = field.key ?? '';
  const map = props.modelValue ?? {};
  if (props.fixedSide === 'key') return map[key] ?? '';
  return Object.keys(map).find((k) => map[k] === key) ?? '';
}

/** 用户改动某字段的可编辑侧：重建该字段条目，其余条目原样保留 */
function onEdit(field: StartInputParam, raw: string) {
  const key = field.key ?? '';
  const value = raw.trim();
  const next: Record<string, string> = { ...(props.modelValue ?? {}) };

  if (props.fixedSide === 'key') {
    if (value) next[key] = value;
    else delete next[key];
  } else {
    const oldK = Object.keys(next).find((k) => next[k] === key);
    if (oldK !== undefined) delete next[oldK];
    if (value) next[value] = key;
  }
  emit('update:modelValue', next);
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <div
      v-for="field in props.fields"
      :key="field.key"
      class="flex items-center gap-2"
    >
      <!-- Output（fixedSide=value）：可编辑侧在左（父上下文键 ← 子产物键） -->
      <ElInput
        v-if="props.fixedSide === 'value'"
        :model-value="editableOf(field)"
        :placeholder="props.editablePlaceholder"
        size="small"
        @update:model-value="onEdit(field, $event)"
      />
      <span v-if="props.fixedSide === 'value'" class="text-gray-400">←</span>

      <!-- 锁定侧：字段名 + 必填星标 + description tooltip -->
      <div class="fixed-field">
        <span v-if="field.required" class="req">*</span>
        <ElTooltip
          :content="field.description"
          :disabled="!field.description"
          placement="top"
        >
          <span class="key">{{ field.key }}</span>
        </ElTooltip>
        <span v-if="field.type" class="type">{{ field.type }}</span>
      </div>

      <!-- Input（fixedSide=key）：可编辑侧在右（子入参键 ← 父上下文键） -->
      <span v-if="props.fixedSide === 'key'" class="text-gray-400">←</span>
      <ElInput
        v-if="props.fixedSide === 'key'"
        :model-value="editableOf(field)"
        :placeholder="props.editablePlaceholder"
        size="small"
        @update:model-value="onEdit(field, $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.fixed-field {
  display: flex;
  flex: 1;
  gap: 4px;
  align-items: center;
  min-width: 0;
  padding: 0 8px;
  overflow: hidden;
  font-size: 12px;
  line-height: 24px;
  white-space: nowrap;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.req {
  color: var(--el-color-danger);
}

.key {
  overflow: hidden;
  font-weight: 500;
  text-overflow: ellipsis;
  color: var(--el-text-color-primary);
}

.type {
  margin-left: auto;
  color: var(--el-text-color-secondary);
}
</style>
