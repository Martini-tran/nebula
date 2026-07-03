<script lang="ts" setup>
/**
 * 可视化条件构造器（受控组件，v-model 绑一个 CondGroup）。
 *
 * IF 节点分支条件、FOR 循环 break 条件共用。一行 = 一个子句：
 *   左值输入 + 运算符下拉 + 右值输入（按运算符自动显隐右值）。
 * 组顶部 AND/OR 切换；「+ 添加条件」增行；每行可删（至少留一行）。
 *
 * 纯前端结构化编辑，不求值。改动即 emit 新的 CondGroup（深拷贝快照由上层落库时做）。
 */
import type { CondClause, CondGroup, CondOp } from '../condition';

import { computed } from 'vue';

import { ElButton, ElInput, ElOption, ElRadioButton, ElRadioGroup, ElSelect } from 'element-plus';

import { COND_OPS, defaultClause, opNeedsRight } from '../condition';

defineOptions({ name: 'ConditionBuilder' });

const props = withDefaults(defineProps<Props>(), {
  leftPlaceholder: '变量引用，如 inputs.intent',
  rightPlaceholder: '值',
});

const emit = defineEmits<{
  'update:modelValue': [value: CondGroup];
}>();

interface Props {
  modelValue: CondGroup;
  leftPlaceholder?: string;
  rightPlaceholder?: string;
}

const clauses = computed(() => props.modelValue.clauses ?? []);

/** 以新 clauses / logic emit 一个完整 CondGroup（浅拷贝子句，避免直接改 props） */
function emitGroup(next: Partial<CondGroup>) {
  emit('update:modelValue', {
    logic: next.logic ?? props.modelValue.logic,
    clauses: (next.clauses ?? clauses.value).map((c) => ({ ...c })),
  });
}

function setLogic(logic: 'AND' | 'OR') {
  emitGroup({ logic });
}

function patchClause(index: number, patch: Partial<CondClause>) {
  const next = clauses.value.map((c, i) => (i === index ? { ...c, ...patch } : { ...c }));
  emitGroup({ clauses: next });
}

function onOpChange(index: number, op: CondOp) {
  // 切到无右值算子时清空右值，避免残留脏值落库
  const patch: Partial<CondClause> = { op };
  if (!opNeedsRight(op)) patch.right = '';
  patchClause(index, patch);
}

function addClause() {
  emitGroup({ clauses: [...clauses.value, defaultClause()] });
}

function removeClause(index: number) {
  const next = clauses.value.filter((_, i) => i !== index);
  emitGroup({ clauses: next.length > 0 ? next : [defaultClause()] });
}
</script>

<template>
  <div class="cond-builder">
    <!-- 组内连接：AND / OR（子句 >1 时才有意义，单条也展示便于预判） -->
    <div v-if="clauses.length > 1" class="cond-logic">
      <span class="cond-logic-label">满足</span>
      <ElRadioGroup
        :model-value="props.modelValue.logic"
        size="small"
        @update:model-value="setLogic($event as 'AND' | 'OR')"
      >
        <ElRadioButton value="AND">全部（AND）</ElRadioButton>
        <ElRadioButton value="OR">任一（OR）</ElRadioButton>
      </ElRadioGroup>
    </div>

    <div class="cond-rows">
      <div v-for="(clause, index) in clauses" :key="index" class="cond-row">
        <ElInput
          class="cond-left"
          :model-value="clause.left"
          :placeholder="props.leftPlaceholder"
          size="small"
          @update:model-value="patchClause(index, { left: $event })"
        />
        <ElSelect
          class="cond-op"
          :model-value="clause.op"
          size="small"
          @update:model-value="onOpChange(index, $event as CondOp)"
        >
          <ElOption
            v-for="op in COND_OPS"
            :key="op.value"
            :label="op.label"
            :value="op.value"
          />
        </ElSelect>
        <ElInput
          v-if="opNeedsRight(clause.op)"
          class="cond-right"
          :model-value="clause.right"
          :placeholder="props.rightPlaceholder"
          size="small"
          @update:model-value="patchClause(index, { right: $event })"
        />
        <span v-else class="cond-right cond-right-empty">—</span>
        <ElButton
          class="cond-del"
          link
          size="small"
          type="danger"
          @click="removeClause(index)"
        >
          删除
        </ElButton>
      </div>
    </div>

    <ElButton size="small" @click="addClause">+ 添加条件</ElButton>
  </div>
</template>

<style scoped>
.cond-builder {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cond-logic {
  display: flex;
  gap: 8px;
  align-items: center;
}

.cond-logic-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cond-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cond-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.cond-left {
  flex: 1 1 40%;
  min-width: 0;
}

.cond-op {
  flex: 0 0 120px;
}

.cond-right {
  flex: 1 1 30%;
  min-width: 0;
}

.cond-right-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-placeholder);
}

.cond-del {
  flex: 0 0 auto;
}
</style>
