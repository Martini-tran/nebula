<script lang="ts" setup>
/**
 * END 结束节点卡片（独立 Vue，结构化摘要风格，仿开始节点卡片）。
 *
 * 「结束」是流程唯一出口：不可删除、只承接上游（无出边，useFlowGraph 的
 * validateConnection 拦截）。它把流程最终输出收敛为固定格式 JSON——
 * 配置入口走壳组件 FlowNodeCard 的右键菜单（EndConfigDialog）。
 *
 * 结构：
 *   - header：出口图标（■）+ 标题（用户命名，缺省「结束」）；
 *   - body：能力指示点（输出 JSON 是否已配置，壳组件计算后传入）。
 *
 * 运行态 runBorderColor 由壳传入，覆盖卡片描边。蓝色主题与开始的青绿呼应、
 * 区别于业务节点。
 */
import { computed } from 'vue';

defineOptions({ name: 'EndNodeCard' });

const props = defineProps<{
  /** skipped 态置灰 */
  dimmed?: boolean;
  /** 能力指示点（壳组件按 nodeConfig.end 计算） */
  features?: EndFeature[];
  /** 运行态边框色（壳传入，空则用默认蓝） */
  runBorderColor?: string;
  /** 展示标题（用户命名，缺省「结束」） */
  title?: string;
}>();

/** 单个能力指示点 */
export interface EndFeature {
  key: string;
  /** 点旁的短标签 */
  label: string;
  /** 是否已配置（亮/暗） */
  active: boolean;
}

const label = computed(() => props.title || '结束');
const borderColor = computed(() => props.runBorderColor || '#5f95ff');
const featureList = computed<EndFeature[]>(() => props.features ?? []);
</script>

<template>
  <div
    class="end-card"
    :class="{ 'is-dimmed': dimmed }"
    :style="{ borderColor }"
  >
    <!-- header：出口图标 + 标题（配置走右键菜单，卡片不放操作入口） -->
    <div class="header">
      <div class="icon">■</div>
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
.end-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  height: 100%;
  padding: 12px;
  overflow: hidden;
  font-family: inter, 'PingFang SC', arial, sans-serif;
  background: linear-gradient(180deg, #f0f5ff, #fff);
  border: 1.5px solid #5f95ff;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgb(95 149 255 / 12%);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.end-card:hover {
  box-shadow: 0 3px 12px rgb(95 149 255 / 24%);
}

.end-card.is-dimmed {
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
  color: #1d39c4;
  background: #f0f5ff;
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

/* 已配置：主题蓝亮起 + 光晕 */
.feat.is-active .feat-dot {
  background: #5f95ff;
  border-color: #5f95ff;
  box-shadow: 0 0 6px rgb(95 149 255 / 55%);
}

.feat.is-active .feat-label {
  color: #1d39c4;
}
</style>
