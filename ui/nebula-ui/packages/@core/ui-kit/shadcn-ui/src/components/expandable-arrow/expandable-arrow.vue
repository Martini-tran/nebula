<script lang="ts" setup>
import { ChevronDown } from '@nebula-core/icons';
import { cn } from '@nebula-core/shared/utils';

const props = defineProps<{
  class?: string;
}>();

// 控制箭头展开/收起状态：true = 已收起
const collapsed = defineModel({ default: false });
</script>

<template>
  <button
    :aria-expanded="!collapsed"
    :class="
      cn(
        'inline-flex cursor-pointer items-center gap-0.5 rounded-sm bg-transparent px-1 py-0.5 text-primary transition-colors',
        'hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        props.class,
      )
    "
    type="button"
    @click="collapsed = !collapsed"
  >
    <slot :is-expanded="collapsed"></slot>
    <span
      :class="{ 'rotate-180': !collapsed }"
      class="inline-flex transition-transform duration-300"
    >
      <slot name="icon">
        <ChevronDown class="size-4" />
      </slot>
    </span>
  </button>
</template>
