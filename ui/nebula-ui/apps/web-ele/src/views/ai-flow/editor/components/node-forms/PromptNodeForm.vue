<script lang="ts" setup>
import type { AiFlowApi } from '#/api';

import { ref } from 'vue';

import {
  ElCollapseTransition,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
} from 'element-plus';

import McpServerSelector from '../selectors/McpServerSelector.vue';
import ProfileSelector from '../selectors/ProfileSelector.vue';

defineOptions({ name: 'PromptNodeForm' });

/** 高级覆盖项（provider/model/baseUrl/apiKey）默认折叠，保持简约 */
const showAdvanced = ref(false);

/** 表单模型：PROMPT 节点全部字段（直写 FlowNodeRaw 顶层） */
const model = defineModel<AiFlowApi.FlowNodeRaw>({ required: true });

/**
 * 关联的 MCP 服务编码列表。落库到 nodeConfig.mcpServerCodes（由 PropertyPanel 读写），
 * 与顶层字段解耦，故单独用一个 model 承载。
 * 注：后端运行时暂未消费该配置，当前仅落库/回显。
 */
const mcpServerCodes = defineModel<string[]>('mcpServerCodes', {
  default: () => [],
});
</script>

<template>
  <div>
    <!-- 提示词 -->
    <section class="prop-section">
      <div class="prop-section-title">提示词</div>
      <div class="prop-grid">
        <ElFormItem class="span-2" label="系统提示">
          <ElInput
            v-model="model.systemPrompt"
            :rows="2"
            placeholder="system prompt"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem class="span-2" label="模板">
          <ElInput
            v-model="model.promptTemplate"
            :rows="4"
            placeholder="支持 #{变量} 占位"
            type="textarea"
          />
        </ElFormItem>
      </div>
    </section>

    <!-- 模型 -->
    <section class="prop-section">
      <div class="prop-section-title">模型</div>
      <div class="prop-grid">
        <ElFormItem label="模型档案">
          <ProfileSelector
            v-model="model.profileCode"
            placeholder="留空用流程默认"
          />
        </ElFormItem>
        <ElFormItem label="关联MCP">
          <McpServerSelector v-model="mcpServerCodes" />
        </ElFormItem>
      </div>

      <div
        class="advanced-toggle"
        @click="showAdvanced = !showAdvanced"
      >
        <span class="chevron" :class="{ open: showAdvanced }">▸</span>
        高级覆盖（provider / model / baseUrl / apiKey）
      </div>
      <ElCollapseTransition>
        <div v-show="showAdvanced" class="prop-grid">
          <ElFormItem label="provider">
            <ElInput v-model="model.provider" placeholder="如 openai" />
          </ElFormItem>
          <ElFormItem label="model">
            <ElInput v-model="model.model" placeholder="如 gpt-4o-mini" />
          </ElFormItem>
          <ElFormItem label="baseUrl">
            <ElInput v-model="model.baseUrl" placeholder="可选" />
          </ElFormItem>
          <ElFormItem label="apiKey">
            <ElInput
              v-model="model.apiKey"
              placeholder="可选"
              show-password
              type="password"
            />
          </ElFormItem>
        </div>
      </ElCollapseTransition>
    </section>

    <!-- 模型参数 -->
    <section class="prop-section">
      <div class="prop-section-title">模型参数</div>
      <div class="prop-grid">
        <ElFormItem label="temperature">
          <ElInputNumber
            v-model="model.temperature"
            :max="2"
            :min="0"
            :step="0.1"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="maxTokens">
          <ElInputNumber
            v-model="model.maxTokens"
            :min="1"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="topP">
          <ElInputNumber
            v-model="model.topP"
            :max="1"
            :min="0"
            :step="0.05"
            controls-position="right"
            style="width: 100%"
          />
        </ElFormItem>
      </div>
    </section>

    <!-- 输出 -->
    <section class="prop-section">
      <div class="prop-section-title">输出</div>
      <div class="prop-grid">
        <ElFormItem label="输出键">
          <ElInput v-model="model.outputKey" placeholder="留空用节点编码" />
        </ElFormItem>
        <ElFormItem label="输出模式">
          <ElSelect v-model="model.outputMode" style="width: 100%">
            <ElOption label="TEXT（整段写入）" value="TEXT" />
            <ElOption label="JSON（解析后逐键展开）" value="JSON" />
          </ElSelect>
        </ElFormItem>
      </div>
    </section>
  </div>
</template>

<style scoped>
.prop-section {
  padding: 4px 0 2px;
}

.prop-section-title {
  margin: 6px 0 10px;
  padding-left: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  border-left: 3px solid var(--type-color, var(--el-color-primary));
}

.prop-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.prop-grid :deep(.el-form-item) {
  margin-bottom: 12px;
  min-width: 0;
}

.prop-grid :deep(.span-2) {
  grid-column: 1 / -1;
}

.advanced-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 2px 0 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  user-select: none;
}

.advanced-toggle:hover {
  color: var(--el-color-primary);
}

.chevron {
  transition: transform 0.15s;
}

.chevron.open {
  transform: rotate(90deg);
}
</style>
