<script lang="ts" setup>
/**
 * IF 条件节点卡片（独立 Vue，分支行列表风格）。
 *
 * 承载「多分支条件判断」：header（IF 图标 + 标题）+ 每分支一行。卡片按分支数
 * 动态长高（node 尺寸由 if-config 的 applyIfNodeShape 设定，卡片撑满 height:100%），
 * 每个分支行右侧对齐一个输出端口（端口 y 由 applyIfNodeShape 按行居中定位）。
 *
 * 行高必须与 if-config 的 IF_HEADER_H / IF_ROW_H 严格一致，否则端口与行错位。
 * 分支数据由壳组件 FlowNodeCard 按 nodeConfig.if 计算后传入（branches），本卡片纯展示。
 */
import { computed } from 'vue';

import { IF_HEADER_H, IF_ROW_H } from '../../if-config';

defineOptions({ name: 'IfNodeCard' });

const props = defineProps<{
  /** 分支行（壳组件按 nodeConfig.if 计算：label + 命中优先级 + 是否已配 + 是否 else） */
  branches?: IfBranchRow[];
  /** skipped 态置灰 */
  dimmed?: boolean;
  /** 运行态边框色（壳传入，空则用默认橙） */
  runBorderColor?: string;
  /** 展示标题（用户命名，缺省「条件判断」） */
  title?: string;
}>();

/** 单个分支行展示数据 */
export interface IfBranchRow {
  id: string;
  /** 分支名 */
  label: string;
  /** 是否已配置条件（else 恒为 true） */
  configured: boolean;
  /** 是否 else 兜底分支 */
  isElse: boolean;
}

const label = computed(() => props.title || '条件判断');
const borderColor = computed(() => props.runBorderColor || '#fa8c16');
const rows = computed<IfBranchRow[]>(() => props.branches ?? []);
</script>

<template>
  <div class="if-card" :class="{ 'is-dimmed': dimmed }" :style="{ borderColor }">
    <!-- header：IF 图标 + 标题（配置走右键菜单） -->
    <div class="header" :style="{ height: `${IF_HEADER_H}px` }">
      <div class="icon">IF</div>
      <div class="title" :title="label">{{ label }}</div>
    </div>

    <!-- 分支行：每行对齐一个右侧输出端口 -->
    <div class="branches">
      <div
        v-for="(b, i) in rows"
        :key="b.id"
        class="branch-row"
        :style="{ height: `${IF_ROW_H}px` }"
      >
        <span class="branch-idx">{{ b.isElse ? '·' : i + 1 }}</span>
        <span class="branch-label" :title="b.label">{{ b.label }}</span>
        <span
          class="branch-dot"
          :class="{ 'is-active': b.configured, 'is-else': b.isElse }"
          :title="b.isElse ? 'else 兜底' : b.configured ? '已配置' : '未配置'"
        ></span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.if-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  padding: 8px 12px;
  overflow: hidden;
  font-family: inter, 'PingFang SC', arial, sans-serif;
  background: linear-gradient(180deg, #fff7e6, #fff);
  border: 1.5px solid #fa8c16;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgb(250 140 22 / 12%);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.if-card:hover {
  box-shadow: 0 3px 12px rgb(250 140 22 / 24%);
}

.if-card.is-dimmed {
  opacity: 0.5;
}

/* header：高度锁定 IF_HEADER_H 减去卡片上内边距，保证端口对齐基准一致 */
.header {
  display: flex;
  flex-shrink: 0;
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
  font-size: 12px;
  font-weight: 600;
  color: #d46b08;
  background: #fff7e6;
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

/* 分支区 */
.branches {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.branch-row {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  overflow: hidden;
}

.branch-idx {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  font-size: 10px;
  font-weight: 600;
  color: #d46b08;
  background: #ffe7ba;
  border-radius: 4px;
}

.branch-label {
  flex: 1;
  overflow: hidden;
  font-size: 12px;
  color: #595959;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 行尾状态点（靠右，与输出端口同侧对齐） */
.branch-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  background: #e8e8e8;
  border: 1px solid #d9d9d9;
  border-radius: 50%;
}

.branch-dot.is-active {
  background: #fa8c16;
  border-color: #fa8c16;
  box-shadow: 0 0 6px rgb(250 140 22 / 55%);
}

.branch-dot.is-else {
  background: #bfbfbf;
  border-color: #bfbfbf;
  box-shadow: none;
}
</style>
