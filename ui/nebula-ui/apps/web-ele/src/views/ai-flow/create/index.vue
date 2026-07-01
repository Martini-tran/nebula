<script lang="ts" setup>
import type { AiFlowApi } from '#/api';

import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

import { getFlowPageApi, saveFlowApi } from '#/api';

import ProfileSelector from '../editor/components/selectors/ProfileSelector.vue';

defineOptions({ name: 'AiFlowCreate' });

const router = useRouter();

/** 第一步：流程基础字段（不含节点/边） */
const form = reactive({
  flowCode: '',
  name: '',
  description: '',
  defaultProfileCode: '',
});

const submitting = ref(false);

/** flowCode 前端查重：列表接口按 keyword 模糊查回后精确等值比对 */
async function isFlowCodeTaken(flowCode: string): Promise<boolean> {
  const res = await getFlowPageApi({
    pageNum: 1,
    pageSize: 100,
    keyword: flowCode,
  });
  return (res.records ?? []).some(
    (r: AiFlowApi.FlowSummaryRaw) => r.flowCode === flowCode,
  );
}

async function handleNext() {
  const flowCode = form.flowCode.trim();
  const name = form.name.trim();
  if (!flowCode) {
    ElMessage.warning('请填写流程编码');
    return;
  }
  if (!name) {
    ElMessage.warning('请填写流程名称');
    return;
  }

  submitting.value = true;
  try {
    if (await isFlowCodeTaken(flowCode)) {
      ElMessage.error(`流程编码「${flowCode}」已被占用`);
      return;
    }
    // 落一条只有头信息、无节点无边的流程，供第二步进编辑器编排
    await saveFlowApi({
      flowCode,
      name,
      description: form.description.trim() || undefined,
      version: 1,
      defaultProfileCode: form.defaultProfileCode || undefined,
      nodes: [],
      edges: [],
    });
    ElMessage.success('基础信息已保存，请继续编排流程');
    // replace：编辑器「返回」直接回列表，不退回本向导页
    router.replace({ name: 'AiFlowEditor', query: { flowCode } });
  } finally {
    submitting.value = false;
  }
}

function handleCancel() {
  router.push({ name: 'AiFlowList' });
}
</script>

<template>
  <Page auto-content-height>
    <ElCard shadow="never">
      <template #header>
        <span class="font-medium">新建流程 · 第一步：基础信息</span>
      </template>

      <ElForm :model="form" class="max-w-2xl" label-width="120px">
        <ElFormItem label="流程编码" required>
          <ElInput
            v-model="form.flowCode"
            maxlength="64"
            placeholder="全局唯一，保存后不可修改"
          />
        </ElFormItem>
        <ElFormItem label="流程名称" required>
          <ElInput v-model="form.name" maxlength="128" placeholder="流程名称" />
        </ElFormItem>
        <ElFormItem label="流程说明">
          <ElInput
            v-model="form.description"
            :rows="3"
            maxlength="255"
            placeholder="流程说明（可选）"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="默认模型档案">
          <ProfileSelector
            v-model="form.defaultProfileCode"
            class="w-full"
            placeholder="节点未指定档案时使用（可选）"
          />
        </ElFormItem>

        <ElFormItem>
          <ElButton :loading="submitting" type="primary" @click="handleNext">
            下一步：编排流程
          </ElButton>
          <ElButton @click="handleCancel">取消</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
  </Page>
</template>
