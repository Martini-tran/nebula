<script lang="ts" setup>
/**
 * AGENT 节点卡片（独立 Vue，结构化摘要风格，仿 LLM / TOOL / 开始节点卡片）。
 *
 * AGENT 节点用于「调用另一个已设计好的 Agent（即已保存的 Workflow）」，实现
 * Workflow 的复用与组合——被调 Agent 自身就是一张完整流程图，本节点只负责
 * 引用它并传入参数。配置入口走壳组件 FlowNodeCard 的右键菜单（配置弹窗后续再补）。
 *
 * 结构：
 *   - header：AGENT 图标 + 标题（用户命名，缺省「Agent」）；
 *   - body：一排能力指示点（壳组件按 nodeConfig 计算后传入，本卡片纯展示）。
 *
 * 运行态 runBorderColor 由壳传入，覆盖卡片描边。紫色主题以区别于 LLM/TOOL 的青绿。
 */
import { computed } from 'vue';

defineOptions({ name: 'AgentNodeCard' });

const props = defineProps<{
  /** skipped 态置灰 */
  dimmed?: boolean;
  /** 能力指示点（壳组件按 nodeConfig 计算） */
  features?: AgentFeature[];
  /** 运行态边框色（壳传入，空则用默认紫） */
  runBorderColor?: string;
  /** 展示标题（用户命名，缺省「Agent」） */
  title?: string;
}>();

/** 单个能力指示点 */
export interface AgentFeature {
  key: string;
  /** 点旁的短标签 */
  label: string;
  /** 是否已配置（亮/暗） */
  active: boolean;
}

const label = computed(() => props.title || 'Agent');
const borderColor = computed(() => props.runBorderColor || '#722ed1');
const featureList = computed<AgentFeature[]>(() => props.features ?? []);
</script>

<template>
  <div
    class="agent-node-card"
    :class="{ 'is-dimmed': dimmed }"
    :style="{ borderColor }"
  >
    <!-- header：AGENT 图标 + 标题（配置走右键菜单，卡片不放操作入口） -->
    <div class="header">
      <div class="icon">AGENT</div>
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
.agent-node-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  height: 100%;
  padding: 12px;
  overflow: hidden;
  font-family: inter, 'PingFang SC', arial, sans-serif;
  background: linear-gradient(180deg, #f9f0ff, #fff);
  border: 1.5px solid #722ed1;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgb(114 46 209 / 12%);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.agent-node-card:hover {
  box-shadow: 0 3px 12px rgb(114 46 209 / 24%);
}

.agent-node-card.is-dimmed {
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
  font-size: 10px;
  font-weight: 600;
  color: #531dab;
  background: #f9f0ff;
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

/* 已配置：主题紫色亮起 + 光晕 */
.feat.is-active .feat-dot {
  background: #722ed1;
  border-color: #722ed1;
  box-shadow: 0 0 6px rgb(114 46 209 / 55%);
}

.feat.is-active .feat-label {
  color: #531dab;
}
</style>
