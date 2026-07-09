<script lang="ts" setup>
import { ref } from 'vue';

import {
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
} from 'element-plus';

import ProfileSelector from './selectors/ProfileSelector.vue';

defineOptions({ name: 'FlowMetaDrawer' });

defineProps<Props>();

interface Props {
  /** 编辑态下流程编码不可修改 */
  isEdit: boolean;
}

/** 各字段独立双向绑定，直接映射到父级 meta，无需额外「应用」步骤 */
const flowCode = defineModel<string>('flowCode', { default: '' });
const name = defineModel<string>('name', { default: '' });
const description = defineModel<string>('description', { default: '' });
const defaultProfileCode = defineModel<string>('defaultProfileCode', {
  default: '',
});
/** 执行内核：DAG（拓扑推进）| STATE_MACHINE（可回跳/成环/挂起） */
const engineType = defineModel<string>('engineType', { default: 'DAG' });
/** 状态机全局转移次数上限，防死循环（仅 STATE_MACHINE 生效） */
const maxTransitions = defineModel<number>('maxTransitions', { default: 100 });

const visible = ref(false);

function open() {
  visible.value = true;
}

function close() {
  visible.value = false;
}

defineExpose({ open, close });
</script>

<template>
  <ElDrawer
    v-model="visible"
    :size="440"
    direction="rtl"
    title="基础信息"
  >
    <ElForm label-position="top">
      <ElFormItem label="流程编码" required>
        <ElInput
          v-model="flowCode"
          :disabled="isEdit"
          maxlength="64"
          placeholder="全局唯一，保存后不可修改"
        />
      </ElFormItem>
      <ElFormItem label="流程名称" required>
        <ElInput v-model="name" maxlength="128" placeholder="流程名称" />
      </ElFormItem>
      <ElFormItem label="流程说明">
        <ElInput
          v-model="description"
          :rows="3"
          maxlength="255"
          placeholder="流程说明（可选）"
          type="textarea"
        />
      </ElFormItem>
      <ElFormItem label="默认模型档案">
        <ProfileSelector
          v-model="defaultProfileCode"
          class="w-full"
          placeholder="节点未指定档案时使用（可选）"
        />
      </ElFormItem>
      <ElFormItem label="执行内核">
        <ElSelect v-model="engineType" class="w-full">
          <ElOption label="DAG（拓扑推进，无环）" value="DAG" />
          <ElOption
            label="状态机（可回跳/成环/挂起）"
            value="STATE_MACHINE"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem v-if="engineType === 'STATE_MACHINE'" label="最大转移次数">
        <ElInputNumber
          v-model="maxTransitions"
          :max="10000"
          :min="1"
          :step="10"
          class="w-full"
        />
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton type="primary" @click="close">关闭</ElButton>
    </template>
  </ElDrawer>
</template>
