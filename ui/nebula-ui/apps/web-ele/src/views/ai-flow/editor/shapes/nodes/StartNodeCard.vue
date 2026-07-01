<script lang="ts" setup>
/**
 * 开始节点卡片（独立 Vue，胶囊风格）。
 *
 * 「开始」是流程唯一入口：不承载业务配置、不可删除、只向下游发起。
 * 因此不复用通用卡片的 header+body 摘要结构，而是渲染一枚绿色圆角胶囊，
 * 居中于 X6 的 260×96 外框内（外框仍是选择/连线命中区，端口不变）。
 *
 * 运行态：__run_state 由壳组件 FlowNodeCard 计算后以 border-color 传入，
 * 这里通过 :style 覆盖胶囊描边即可。
 */
import { computed } from 'vue';

defineOptions({ name: 'StartNodeCard' });

const props = defineProps<{
  /** 展示标题（用户命名，缺省「开始」） */
  title?: string;
  /** 运行态边框色（壳传入，空则用默认绿） */
  runBorderColor?: string;
  /** skipped 态置灰 */
  dimmed?: boolean;
}>();

const label = computed(() => props.title || '开始');
const borderColor = computed(() => props.runBorderColor || '#13c2c2');
</script>

<template>
  <div class="start-wrap" :class="{ 'is-dimmed': dimmed }">
    <div class="start-capsule" :style="{ borderColor }">
      <span class="start-dot"></span>
      <span class="start-label" :title="label">{{ label }}</span>
    </div>
  </div>
</template>

<style scoped>
.start-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.start-wrap.is-dimmed {
  opacity: 0.5;
}

.start-capsule {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 10px 22px;
  font-family: inter, 'PingFang SC', arial, sans-serif;
  background: linear-gradient(180deg, #e6fffb, #d3f5f0);
  border: 1.5px solid #13c2c2;
  border-radius: 999px;
  box-shadow: 0 2px 8px rgb(19 194 194 / 20%);
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.start-capsule:hover {
  box-shadow: 0 4px 14px rgb(19 194 194 / 30%);
}

.start-dot {
  display: inline-block;
  flex-shrink: 0;
  width: 9px;
  height: 9px;
  background: #13c2c2;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgb(19 194 194 / 20%);
}

.start-label {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #0a7a7a;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
