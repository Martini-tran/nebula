<script lang="ts" setup>
/**
 * AGENT_REACT 节点卡片（ReAct 里程碑执行，结构化摘要风格，仿 TOOL / LLM 卡片）。
 *
 * 与 TOOL 卡片的语义差别：TOOL 是「固定调一个工具」，本节点是「给模型一批工具白名单，
 * 由模型自主多轮选调」（ReAct loop）。故摘要展示的是 **模型档案 + 工具数量**，
 * 而非某一个具体工具编码。
 *
 * 结构：header（AGENT_REACT 图标 + 标题）+ summary（模型 · 工具×N｜输出键·模式）
 *      + body（能力指示点：Model / Tools / Prompt / Output）。
 * 指示点与摘要均由壳组件 FlowNodeCard 计算后传入，本卡片纯展示。
 */
import { computed } from 'vue';

defineOptions({ name: 'AgentReactNodeCard' });

const props = defineProps<{
  /** skipped 态置灰 */
  dimmed?: boolean;
  /** 能力指示点（壳组件按 nodeConfig 计算） */
  features?: AgentReactFeature[];
  /** 输出摘要：输出键 · 模式 */
  output?: string;
  /** 运行态边框色（壳传入，空则用默认紫） */
  runBorderColor?: string;
  /** 带值摘要：模型档案 · 工具×N（壳组件计算） */
  summary?: string;
  /** 展示标题（用户命名，缺省「ReAct 执行」） */
  title?: string;
}>();

/** 单个能力指示点 */
export interface AgentReactFeature {
  key: string;
  /** 点旁的短标签 */
  label: string;
  /** 是否已配置（亮/暗） */
  active: boolean;
}

const label = computed(() => props.title || 'ReAct 执行');
const borderColor = computed(() => props.runBorderColor || '#722ed1');
const featureList = computed<AgentReactFeature[]>(() => props.features ?? []);
</script>

<template>
  <div
    class="react-card"
    :class="{ 'is-dimmed': dimmed }"
    :style="{ borderColor }"
  >
    <!-- header：AGENT_REACT 图标 + 标题（配置走右键菜单/右侧面板） -->
    <div class="header">
      <div class="icon">ReAct</div>
      <div class="title" :title="label">{{ label }}</div>
    </div>

    <!-- summary：模型档案 · 工具×N + 输出键·模式 -->
    <div v-if="summary || output" class="summary">
      <span class="model" :title="summary">{{ summary }}</span>
      <span v-if="output" class="out" :title="output">{{ output }}</span>
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
.react-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 7px;
  width: 100%;
  height: 100%;
  padding: 10px 12px;
  overflow: hidden;
  font-family: inter, 'PingFang SC', arial, sans-serif;
  background: linear-gradient(180deg, #faf5ff, #fff);
  border: 1.5px solid #722ed1;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgb(114 46 209 / 12%);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.react-card:hover {
  box-shadow: 0 3px 12px rgb(114 46 209 / 24%);
}

.react-card.is-dimmed {
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

/* summary：带值摘要行 */
.summary {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  font-size: 12px;
  color: #595959;
}

.summary .model {
  flex: 0 1 auto;
  overflow: hidden;
  font-weight: 500;
  color: #531dab;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary .out {
  flex-shrink: 0;
  margin-left: auto;
  overflow: hidden;
  color: #8c8c8c;
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

/* 已配置：主题紫亮起 + 光晕 */
.feat.is-active .feat-dot {
  background: #722ed1;
  border-color: #722ed1;
  box-shadow: 0 0 6px rgb(114 46 209 / 55%);
}

.feat.is-active .feat-label {
  color: #531dab;
}
</style>
