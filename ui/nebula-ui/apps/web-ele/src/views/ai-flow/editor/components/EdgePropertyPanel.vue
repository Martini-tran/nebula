<script lang="ts" setup>
import { ElAlert, ElFormItem, ElInput } from 'element-plus';

defineOptions({ name: 'EdgePropertyPanel' });

const model = defineModel<{ condition_expr: string }>({ required: true });
</script>

<template>
  <div>
    <ElFormItem label="条件表达式">
      <ElInput
        v-model="model.condition_expr"
        :rows="3"
        placeholder="SpEL 表达式，留空表示无条件直连"
        type="textarea"
      />
    </ElFormItem>
    <ElAlert
      :closable="false"
      title="分支说明"
      type="info"
      description="连线上的 SpEL 条件成立时，下游节点才可达。根对象为编排上下文，可用 getString('key')、contains('key') 等；例如 getString('intent') == 'series'。"
    />
  </div>
</template>
