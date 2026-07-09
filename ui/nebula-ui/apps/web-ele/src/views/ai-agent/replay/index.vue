<script lang="ts" setup>
/**
 * 实例回放页：在只读 X6 画布上按转移历史逐步重现智能体实例的执行路径。
 *
 * 图从实例的 graph_snapshot（版本锁定的完整源图定义，含画布坐标）加载，
 * 绝不回查当前流程定义——与后端"续跑/回放只从 snapshot 加载图"的语义一致，
 * 流程后来被改过也不影响回放还原度。
 *
 * 布局：顶部实例信息条 + 左侧画布（回放控制条悬浮） + 右侧转移时间线（点击
 * 跳步，与画布双向联动）与上下文快照。
 */
import type { AiAgentApi, AiFlowApi } from '#/api';

import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElButtonGroup,
  ElCollapse,
  ElCollapseItem,
  ElEmpty,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag,
} from 'element-plus';

import { getAgentInstanceDetailApi } from '#/api';

import { flowToGraph } from '../../ai-flow/editor/codec';
import TransitionTimeline from '../components/TransitionTimeline.vue';
import { usePlayback } from './usePlayback';
import { useReplayGraph } from './useReplayGraph';

defineOptions({ name: 'AiAgentReplay' });

const route = useRoute();

const STATUS_TAG: Record<string, string> = {
  FAILED: 'danger',
  RUNNING: 'primary',
  SUCCESS: 'success',
  SUSPENDED: 'warning',
};

const loading = ref(false);
const loadError = ref('');
const detail = ref<AiAgentApi.AgentInstance>();

const canvasRef = ref<HTMLElement>();
const { graphRef, init } = useReplayGraph(canvasRef);
const playback = usePlayback({ getGraph: () => graphRef.value });

const contextText = computed(() =>
  detail.value?.contextSnapshot
    ? JSON.stringify(detail.value.contextSnapshot, null, 2)
    : '',
);

async function load() {
  const instanceId = String(route.query.instanceId ?? '');
  if (!instanceId) {
    loadError.value = '缺少 instanceId 参数';
    return;
  }
  loading.value = true;
  try {
    const d = await getAgentInstanceDetailApi(instanceId);
    detail.value = d;
    if (!d.graphSnapshot) {
      loadError.value = '该实例缺少图快照（graph_snapshot），无法画布回放';
      return;
    }
    let def: AiFlowApi.FlowDefinitionRaw;
    try {
      def = JSON.parse(d.graphSnapshot);
    } catch {
      loadError.value = '图快照解析失败（非法 JSON）';
      return;
    }
    await nextTick();
    const g = init();
    if (!g) return;
    g.fromJSON(flowToGraph(def));
    g.centerContent();
    playback.setTransitions(d.transitions ?? []);
  } finally {
    loading.value = false;
  }
}

/** 时间线点击 → 跳到该步（index 与 playback.steps 对齐） */
function onTimelineSelect(index: number) {
  playback.pause();
  playback.applyStep(index);
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <div class="replay-root">
      <!-- 实例信息条 -->
      <div v-if="detail" class="replay-header">
        <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
          <span>
            <span class="text-gray-400">实例：</span>{{ detail.instanceId }}
          </span>
          <span>
            <span class="text-gray-400">Agent：</span>{{ detail.agentCode
            }}<span v-if="detail.agentVersion" class="text-xs text-gray-400">
              @v{{ detail.agentVersion }}
            </span>
          </span>
          <span>
            <span class="text-gray-400">Flow：</span>{{ detail.flowCode
            }}<span v-if="detail.flowVersion" class="text-xs text-gray-400">
              @v{{ detail.flowVersion }}
            </span>
          </span>
          <ElTag
            :type="(STATUS_TAG[detail.status ?? ''] as any) || 'info'"
            size="small"
          >
            {{ detail.status }}
          </ElTag>
          <span v-if="detail.status === 'SUSPENDED' && detail.awaitingEvents?.length">
            <span class="text-gray-400">等待事件：</span>
            <ElTag
              v-for="e in detail.awaitingEvents"
              :key="e"
              class="mr-1"
              size="small"
              type="warning"
            >
              {{ e }}
            </ElTag>
          </span>
          <span v-if="detail.errorMsg" class="text-xs text-red-500">
            {{ detail.errorMsg }}
          </span>
        </div>
      </div>

      <div v-if="loadError" class="flex flex-1 items-center justify-center">
        <ElEmpty :description="loadError" />
      </div>

      <div v-else class="replay-body">
        <!-- 画布 + 悬浮控制条 -->
        <div class="replay-canvas-wrap">
          <div ref="canvasRef" class="replay-canvas"></div>

          <div class="replay-controls">
            <ElButtonGroup size="small">
              <ElButton
                :disabled="playback.cursor.value < 0"
                size="small"
                @click="playback.prev"
              >
                上一步
              </ElButton>
              <ElButton
                v-if="!playback.playing.value"
                size="small"
                type="primary"
                @click="playback.play"
              >
                播放
              </ElButton>
              <ElButton v-else size="small" type="warning" @click="playback.pause">
                暂停
              </ElButton>
              <ElButton
                :disabled="playback.atEnd.value"
                size="small"
                @click="playback.next"
              >
                下一步
              </ElButton>
            </ElButtonGroup>
            <ElButton size="small" @click="playback.resetToStart">重置</ElButton>
            <ElButton size="small" @click="playback.toEnd">末步</ElButton>
            <ElSelect
              :model-value="playback.speed.value"
              size="small"
              style="width: 76px"
              @update:model-value="playback.setSpeed"
            >
              <ElOption :value="1" label="1x" />
              <ElOption :value="2" label="2x" />
              <ElOption :value="4" label="4x" />
            </ElSelect>
            <span class="ml-2 flex items-center gap-1 text-xs text-gray-500">
              折叠重试
              <ElSwitch
                :model-value="playback.collapseRetry.value"
                size="small"
                @update:model-value="playback.setCollapseRetry($event as boolean)"
              />
            </span>
            <span class="ml-auto text-xs text-gray-500">
              步骤 {{ playback.cursor.value + 1 }} / {{ playback.steps.value.length }}
            </span>
          </div>
        </div>

        <!-- 右侧：时间线（联动） + 上下文快照 -->
        <div class="replay-side">
          <div class="mb-1 text-sm font-medium">转移时间线</div>
          <div class="replay-timeline">
            <TransitionTimeline
              :active-seq="playback.activeSeq.value"
              :transitions="playback.steps.value"
              @select="onTimelineSelect"
            />
          </div>
          <ElCollapse class="mt-2">
            <ElCollapseItem name="ctx" title="上下文快照">
              <pre class="ctx-pre">{{ contextText || '（空）' }}</pre>
            </ElCollapseItem>
          </ElCollapse>
        </div>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.replay-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--el-bg-color);
  border-radius: 8px;
}

.replay-header {
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.replay-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.replay-canvas-wrap {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.replay-canvas {
  flex: 1;
  min-height: 0;
}

.replay-controls {
  position: absolute;
  bottom: 12px;
  left: 12px;
  right: 12px;
  z-index: 10;
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-lighter);
}

.replay-side {
  display: flex;
  flex-direction: column;
  width: 380px;
  min-height: 0;
  padding: 12px;
  overflow: hidden;
  border-left: 1px solid var(--el-border-color-lighter);
}

.replay-timeline {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.ctx-pre {
  max-height: 26vh;
  padding: 8px;
  overflow: auto;
  font-size: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
