<script lang="ts" setup>
/**
 * 实例转移历史时间线（从 AgentInstanceDrawer 抽出，供详情抽屉与画布回放页共用）。
 *
 * - transitions 按 seq 升序渲染，outcome 上色（SUCCESS/RETRY/FAILED/COMPENSATED）。
 * - activeSeq（可选）：回放页联动——高亮当前步骤并自动滚动到可见位置；
 *   点击任一条目发出 select(index) 供回放跳步（index 为升序数组下标）。
 */
import type { AiAgentApi } from '#/api';

import { computed, nextTick, ref, watch } from 'vue';

import { ElEmpty, ElTag, ElTimeline, ElTimelineItem } from 'element-plus';

defineOptions({ name: 'TransitionTimeline' });

const props = withDefaults(
  defineProps<{
    transitions?: AiAgentApi.AgentTransition[];
    /** 当前回放步骤的 seq（不传则纯展示，不联动） */
    activeSeq?: null | number;
  }>(),
  { transitions: () => [], activeSeq: null },
);

const emit = defineEmits<{
  /** 点击条目：index 为升序数组下标（供回放跳步） */
  select: [index: number];
}>();

/** 按 seq 升序（重试也占号） */
const sorted = computed(() =>
  [...(props.transitions ?? [])].sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0)),
);

const OUTCOME_COLOR: Record<string, string> = {
  COMPENSATED: '#909399',
  FAILED: '#f56c6c',
  RETRY: '#e6a23c',
  SUCCESS: '#67c23a',
};

function tagType(outcome?: string) {
  if (outcome === 'SUCCESS') return 'success';
  if (outcome === 'RETRY') return 'warning';
  return 'danger';
}

function nodeResultText(t: AiAgentApi.AgentTransition) {
  return t.nodeResult && Object.keys(t.nodeResult).length > 0
    ? JSON.stringify(t.nodeResult, null, 2)
    : '';
}

/** 当前步变化时滚动到可见位置 */
const itemRefs = ref<Map<number, HTMLElement>>(new Map());

function setItemRef(index: number, el: unknown) {
  if (el instanceof HTMLElement) itemRefs.value.set(index, el);
  else itemRefs.value.delete(index);
}

watch(
  () => props.activeSeq,
  async (seq) => {
    if (seq === null || seq === undefined) return;
    const index = sorted.value.findIndex((t) => t.seq === seq);
    if (index < 0) return;
    await nextTick();
    itemRefs.value
      .get(index)
      ?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  },
);
</script>

<template>
  <ElEmpty
    v-if="sorted.length === 0"
    :image-size="60"
    description="暂无转移记录"
  />
  <ElTimeline v-else class="mt-2">
    <ElTimelineItem
      v-for="(t, index) in sorted"
      :key="`${t.seq}-${t.attempt}-${t.outcome}`"
      :color="OUTCOME_COLOR[t.outcome ?? ''] || '#909399'"
      :timestamp="`seq ${t.seq} · attempt ${t.attempt}`"
    >
      <div
        :ref="(el) => setItemRef(index, el)"
        :class="{ 'timeline-active': props.activeSeq === t.seq }"
        class="timeline-entry"
        @click="emit('select', index)"
      >
        <div class="flex items-center gap-2 text-sm">
          <ElTag :type="tagType(t.outcome)" size="small">
            {{ t.outcome }}
          </ElTag>
          <span class="font-medium">
            {{ t.fromState ? `${t.fromState} → ` : '' }}{{ t.toState }}
          </span>
          <span v-if="t.eventName" class="text-xs text-gray-400">
            事件 {{ t.eventName }}
          </span>
        </div>
        <pre
          v-if="nodeResultText(t)"
          class="mt-1 max-h-[24vh] overflow-auto rounded bg-[#f5f5f5] p-2 text-xs dark:bg-[#2a2a2a]"
          >{{ nodeResultText(t) }}</pre>
      </div>
    </ElTimelineItem>
  </ElTimeline>
</template>

<style scoped>
.timeline-entry {
  padding: 4px 6px;
  margin: -4px -6px;
  cursor: pointer;
  border-radius: 6px;
}

.timeline-entry:hover {
  background: var(--el-fill-color-light);
}

.timeline-active {
  background: var(--el-color-primary-light-9);
  outline: 1px solid var(--el-color-primary-light-5);
}
</style>
