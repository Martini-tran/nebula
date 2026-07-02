<script lang="ts" setup>
/**
 * 开始节点卡片（独立 Vue，结构化摘要风格）。
 *
 * 「开始」是流程唯一入口：不可删除、只向下游发起，但它并非「空节点」——
 * 它承载流程入参（后续还会承载前置条件）。因此不再用一枚圆形/胶囊表达，
 * 而是复用与通用卡片同构的 header + body 结构：
 *   - header：入口图标（▶）+ 标题；
 *   - body：逐行列出已定义入参（参数名 · 类型），无入参时给引导占位。
 * 卡片仍居中于 X6 的 260×96 外框内（外框是选择/连线命中区，端口不变）。
 *
 * 入参数据约定：从 node.data.nodeConfig.inputs 归一化后传入（normalizeStartInputs），
 * 每项形如 { name, type }。配置入口在壳组件 FlowNodeCard 的右键菜单（打开
 * StartConfigDialog），本卡片纯展示。
 *
 * 运行态：runBorderColor 由壳组件 FlowNodeCard 计算后传入，覆盖卡片描边。
 */
import { computed } from 'vue';

defineOptions({ name: 'StartNodeCard' });

const props = defineProps<{
  /** skipped 态置灰 */
  dimmed?: boolean;
  /** 已定义入参（node.data.nodeConfig.inputs） */
  inputs?: StartInput[];
  /** 运行态边框色（壳传入，空则用默认绿） */
  runBorderColor?: string;
  /** 展示标题（用户命名，缺省「开始」） */
  title?: string;
}>();

/** 单条入参的最小展示结构 */
interface StartInput {
  name?: string;
  type?: string;
}

const label = computed(() => props.title || '开始');
const borderColor = computed(() => props.runBorderColor || '#13c2c2');

/** 归一化入参列表（过滤空项） */
const inputList = computed<StartInput[]>(() =>
  (props.inputs ?? []).filter((it) => it && (it.name || it.type)),
);

/** body 内最多平铺展示的入参条数，超出折叠为「+N」 */
const MAX_VISIBLE = 3;
const visibleInputs = computed(() => inputList.value.slice(0, MAX_VISIBLE));
const overflowCount = computed(() =>
  Math.max(0, inputList.value.length - MAX_VISIBLE),
);
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

    <!-- body：入参摘要，无入参时引导占位 -->
    <div class="body">
      <template v-if="inputList.length > 0">
        <span
          v-for="(it, i) in visibleInputs"
          :key="i"
          class="param-chip"
          :title="`${it.name || '参数'} · ${it.type || 'string'}`"
        >
          <span class="param-name">{{ it.name || '参数' }}</span>
          <span class="param-type">{{ it.type || 'string' }}</span>
        </span>
        <span v-if="overflowCount > 0" class="param-more">
          +{{ overflowCount }}
        </span>
      </template>
      <span v-else class="empty-tip">未定义入参</span>
    </div>
  </div>
</template>

<style scoped>
.start-card {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 8px;
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

/* body：入参摘要 */
.body {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
}

.param-chip {
  display: inline-flex;
  flex-shrink: 0;
  gap: 4px;
  align-items: center;
  max-width: 120px;
  padding: 1px 8px;
  overflow: hidden;
  line-height: 18px;
  background: #f5f5f5;
  border-radius: 4px;
}

.param-name {
  overflow: hidden;
  font-weight: 500;
  color: #141414;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-type {
  flex-shrink: 0;
  font-size: 11px;
  color: #8c8c8c;
}

.param-more {
  flex-shrink: 0;
  padding: 1px 6px;
  font-size: 11px;
  line-height: 18px;
  color: #08979c;
  background: #e6fffb;
  border-radius: 4px;
}

.empty-tip {
  color: #bfbfbf;
}
</style>
