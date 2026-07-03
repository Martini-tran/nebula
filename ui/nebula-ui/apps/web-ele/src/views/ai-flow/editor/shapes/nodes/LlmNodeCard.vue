<script lang="ts" setup>
/**
 * LLM 节点卡片（独立 Vue，结构化摘要风格，仿开始节点卡片）。
 *
 * 与通用卡片不同：LLM 节点承载模型档案、提示词等 Agent 级配置，配置入口
 * 走壳组件 FlowNodeCard 的右键菜单（打开 LlmConfigDialog）。结构：
 *   - header：LLM 图标 + 标题（用户命名，缺省「LLM」）；
 *   - body：一排能力指示点（当前预留，后续对应模型/提示词等配置点亮）。
 *
 * 指示点数据由壳组件 FlowNodeCard 按 nodeConfig 计算后传入（features），
 * 本卡片纯展示。运行态 runBorderColor 由壳传入，覆盖卡片描边。
 */
import { computed } from 'vue';

defineOptions({ name: 'LlmNodeCard' });

const props = defineProps<{
  /** skipped 态置灰 */
  dimmed?: boolean;
  /** 能力指示点（壳组件按 nodeConfig 计算） */
  features?: LlmFeature[];
  /** 运行态边框色（壳传入，空则用默认绿） */
  runBorderColor?: string;
  /** 展示标题（用户命名，缺省「LLM」） */
  title?: string;
}>();

/** 单个能力指示点 */
export interface LlmFeature {
  key: string;
  /** 点旁的短标签 */
  label: string;
  /** 是否已配置（亮/暗） */
  active: boolean;
}

const label = computed(() => props.title || 'LLM');
const borderColor = computed(() => props.runBorderColor || '#13c2c2');
const featureList = computed<LlmFeature[]>(() => props.features ?? []);
</script>

<template>
  <div class="llm-card" :class="{ 'is-dimmed': dimmed }" :style="{ borderColor }">
    <!-- header：LLM 图标 + 标题（配置走右键菜单，卡片不放操作入口） -->
    <div class="header">
      <div class="icon">LLM</div>
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
.llm-card {
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

.llm-card:hover {
  box-shadow: 0 3px 12px rgb(19 194 194 / 24%);
}

.llm-card.is-dimmed {
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
  font-size: 11px;
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
