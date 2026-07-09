<script lang="ts" setup>
/**
 * 开始节点卡片（独立 Vue，结构化摘要风格）。
 *
 * 「开始」是流程唯一入口：不可删除、只向下游发起，但它并非「空节点」——
 * 它承载入参、记忆、数据、工具、MCP 等 Agent 级配置。结构：
 *   - header：入口图标（▶）+ 标题（保持不变）；
 *   - body：一排能力指示点，每个点对应一类配置（入参/记忆/数据/工具/MCP），
 *     已配置则亮起（主题青色+光晕），未配置则暗淡（灰点）。
 * 卡片仍居中于 X6 的 260×96 外框内（外框是选择/连线命中区，端口不变）。
 *
 * 指示点数据由壳组件 FlowNodeCard 按 nodeConfig 各键计算后传入（features），
 * 本卡片纯展示；配置入口在壳组件的右键菜单（打开 StartConfigDialog 等）。
 *
 * 运行态：runBorderColor 由壳组件 FlowNodeCard 计算后传入，覆盖卡片描边。
 */
import { computed } from 'vue';

defineOptions({ name: 'StartNodeCard' });

const props = defineProps<{
  /** skipped 态置灰 */
  dimmed?: boolean;
  /** 能力指示点（壳组件按 nodeConfig 计算） */
  features?: StartFeature[];
  /** 运行态边框色（壳传入，空则用默认绿） */
  runBorderColor?: string;
  /** 展示标题（用户命名，缺省「开始」） */
  title?: string;
}>();

/** 单个能力指示点 */
export interface StartFeature {
  key: string;
  /** 点旁的短标签（入参/记忆/数据/工具/MCP） */
  label: string;
  /** 是否已配置（亮/暗） */
  active: boolean;
}

const label = computed(() => props.title || '开始');
const borderColor = computed(() => props.runBorderColor || '#13c2c2');
const featureList = computed<StartFeature[]>(() => props.features ?? []);
</script>

<template>
  <div
    class="start-card"
    :class="{ 'is-dimmed': dimmed }"
    :style="{ borderColor }"
  >
    <!-- header：入口图标 + 标题（配置走右键菜单，卡片不放操作入口） -->
    <div class="header">
      <div class="icon">▶</div>
      <div class="title" :title="label">{{ label }}</div>
    </div>

    <!-- body：能力指示点，已配置亮起、未配置暗淡 -->
    <div class="body">
      <span
        v-for="f in featureList"
        :key="f.key"
        class="feat"
        :class="{ 'is-active': f.active }"
        :title="`${f.label}：${f.active ? '已配置' : '未配置'}`"
      >
        <span class="feat-dot"></span>
        <span class="feat-label">{{ f.label }}</span>
      </span>
    </div>
  </div>
</template>

<style scoped>
.start-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  height: 100%;
  padding: 12px;
  overflow: hidden;
  font-family: inter, 'PingFang SC', arial, sans-serif;
  background: linear-gradient(180deg, #f0fffd, #fff);
  border: 1.5px solid #13c2c2;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgb(19 194 194 / 12%);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.start-card:hover {
  box-shadow: 0 3px 12px rgb(19 194 194 / 24%);
}

.start-card.is-dimmed {
  opacity: 0.5;
}

/* header */
.header {
  display: flex;
  gap: 10px;
  align-items: center;
}

.icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding-left: 2px;
  font-size: 13px;
  font-weight: 600;
  color: #08979c;
  background: #e6fffb;
  border-radius: 8px;
}

.title {
  flex: 1;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #141414;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* body：能力指示点 */
.body {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  overflow: hidden;
}

.feat {
  display: inline-flex;
  flex-shrink: 0;
  gap: 4px;
  align-items: center;
}

/* 未配置：暗淡的灰白点 */
.feat-dot {
  width: 8px;
  height: 8px;
  background: #e8e8e8;
  border: 1px solid #d9d9d9;
  border-radius: 50%;
  transition:
    background-color 0.2s,
    border-color 0.2s,
    box-shadow 0.2s;
}

.feat-label {
  font-size: 11px;
  color: #bfbfbf;
  white-space: nowrap;
  transition: color 0.2s;
}

/* 已配置：主题青色亮起 + 光晕 */
.feat.is-active .feat-dot {
  background: #13c2c2;
  border-color: #13c2c2;
  box-shadow: 0 0 6px rgb(19 194 194 / 55%);
}

.feat.is-active .feat-label {
  color: #08979c;
}
</style>
