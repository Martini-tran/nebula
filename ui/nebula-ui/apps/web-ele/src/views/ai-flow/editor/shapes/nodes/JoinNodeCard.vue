<script lang="ts" setup>
/**
 * JOIN 汇总节点卡片（独立 Vue，结构化摘要风格，仿 AGENT / LLM / TOOL 卡片）。
 *
 * JOIN 节点承载「并行分支汇聚（fan-in）」：多条并行链路汇入本节点同步，
 * 按策略（全部/任一、失败容忍、超时）决定何时放行后继。配置入口走壳组件
 * FlowNodeCard 的右键菜单（JoinConfigDialog）。
 *
 * 结构：
 *   - header：JOIN 图标 + 标题（用户命名，缺省「汇总」）；
 *   - body：一排能力指示点（壳组件按 nodeConfig.join 计算后传入，本卡片纯展示）。
 *
 * 运行态 runBorderColor 由壳传入，覆盖卡片描边。珊瑚红主题（THEME_COLORS.red）
 * 以区别于 IF 的橙与 AGENT 的紫。
 */
import { computed } from 'vue';

defineOptions({ name: 'JoinNodeCard' });

const props = defineProps<{
  /** skipped 态置灰 */
  dimmed?: boolean;
  /** 能力指示点（壳组件按 nodeConfig.join 计算） */
  features?: JoinFeature[];
  /** 运行态边框色（壳传入，空则用默认珊瑚红） */
  runBorderColor?: string;
  /** 展示标题（用户命名，缺省「汇总」） */
  title?: string;
}>();

/** 单个能力指示点 */
export interface JoinFeature {
  key: string;
  /** 点旁的短标签 */
  label: string;
  /** 是否已配置（亮/暗） */
  active: boolean;
}

const label = computed(() => props.title || '汇总');
const borderColor = computed(() => props.runBorderColor || '#ff7875');
const featureList = computed<JoinFeature[]>(() => props.features ?? []);
</script>

<template>
  <div
    class="join-node-card"
    :class="{ 'is-dimmed': dimmed }"
    :style="{ borderColor }"
  >
    <!-- header：JOIN 图标 + 标题（配置走右键菜单，卡片不放操作入口） -->
    <div class="header">
      <div class="icon">JOIN</div>
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
.join-node-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  height: 100%;
  padding: 12px;
  overflow: hidden;
  font-family: inter, 'PingFang SC', arial, sans-serif;
  background: linear-gradient(180deg, #fff1f0, #fff);
  border: 1.5px solid #ff7875;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgb(255 120 117 / 12%);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.join-node-card:hover {
  box-shadow: 0 3px 12px rgb(255 120 117 / 24%);
}

.join-node-card.is-dimmed {
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
  color: #cf1322;
  background: #fff1f0;
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

/* 已配置：主题珊瑚红亮起 + 光晕 */
.feat.is-active .feat-dot {
  background: #ff7875;
  border-color: #ff7875;
  box-shadow: 0 0 6px rgb(255 120 117 / 55%);
}

.feat.is-active .feat-label {
  color: #cf1322;
}
</style>
