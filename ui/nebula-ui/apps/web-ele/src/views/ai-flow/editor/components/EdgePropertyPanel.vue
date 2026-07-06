<script lang="ts" setup>
import { ElFormItem, ElInput } from 'element-plus';

defineOptions({ name: 'EdgePropertyPanel' });

const model = defineModel<{ conditionExpr: string; eventName?: string }>({
  required: true,
});
</script>

<template>
  <div>
    <ElFormItem label="条件表达式">
      <ElInput
        v-model="model.conditionExpr"
        :rows="3"
        placeholder="SpEL 表达式，留空表示无条件直连"
        type="textarea"
      />
    </ElFormItem>
    <ElFormItem label="事件名">
      <ElInput
        v-model="model.eventName"
        placeholder="状态机 signal 唤醒时匹配（可选，DAG 忽略）"
      />
    </ElFormItem>
  </div>
</template>
