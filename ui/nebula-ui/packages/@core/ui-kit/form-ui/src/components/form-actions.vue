<script setup lang="ts">
import { computed, toRaw, unref, watch } from 'vue';

import { useSimpleLocale } from '@nebula-core/composables';
import { ChevronDown } from '@nebula-core/icons';
import { cn, isFunction, triggerWindowResize } from '@nebula-core/shared/utils';

import { COMPONENT_MAP } from '../config';
import { injectFormProps } from '../use-form-context';

// 注意：文件内多处函数里有局部的 `const props = unref(rootProps)`，
// 这里另起名字，避免顶层 props 被遮蔽后读串
const actionProps = withDefaults(
  defineProps<{
    /** 是否真的有字段可被折叠，由 form-render 计算后传入 */
    canCollapse?: boolean;
  }>(),
  { canCollapse: false },
);

const { $t } = useSimpleLocale();

const [rootProps, form] = injectFormProps();

const collapsed = defineModel({ default: false });

// 开关打开、且确实有东西可折叠，才渲染「展开/收起」
const showCollapse = computed(
  () => !!unref(rootProps).showCollapseButton && actionProps.canCollapse,
);

const resetButtonOptions = computed(() => {
  return {
    content: `${$t.value('reset')}`,
    show: true,
    ...unref(rootProps).resetButtonOptions,
  };
});

const submitButtonOptions = computed(() => {
  return {
    content: `${$t.value('submit')}`,
    show: true,
    ...unref(rootProps).submitButtonOptions,
  };
});

async function handleSubmit(e: Event) {
  e?.preventDefault();
  e?.stopPropagation();
  const props = unref(rootProps);
  if (!props.formApi) {
    return;
  }

  const { valid } = await props.formApi.validate();
  if (!valid) {
    return;
  }

  const values = toRaw(await props.formApi.getValues()) ?? {};
  await props.handleSubmit?.(values);
}

async function handleReset(e: Event) {
  e?.preventDefault();
  e?.stopPropagation();
  const props = unref(rootProps);

  const values = toRaw(await props.formApi?.getValues()) ?? {};

  if (isFunction(props.handleReset)) {
    await props.handleReset?.(values);
  } else {
    form.resetForm();
  }
}

watch(
  () => collapsed.value,
  () => {
    const props = unref(rootProps);
    if (props.collapseTriggerResize) {
      triggerWindowResize();
    }
  },
);

const actionWrapperClass = computed(() => {
  const props = unref(rootProps);
  const actionLayout = props.actionLayout || 'rowEnd';
  const actionPosition = props.actionPosition || 'right';

  const cls = [
    'flex',
    'items-center',
    'gap-3',
    props.compact ? 'pb-2' : 'pb-4',
    props.layout === 'vertical' ? 'self-end' : 'self-center',
    props.layout === 'inline' ? '' : 'w-full',
    props.actionWrapperClass,
  ];

  switch (actionLayout) {
    case 'newLine': {
      cls.push('col-span-full');
      break;
    }
    case 'rowEnd': {
      cls.push('col-[-2/-1]');
      break;
    }
    // 'inline' 不需要额外类名，保持默认
  }

  switch (actionPosition) {
    case 'center': {
      cls.push('justify-center');
      break;
    }
    case 'left': {
      cls.push('justify-start');
      break;
    }
    default: {
      // case 'right': 默认右对齐
      cls.push('justify-end');
      break;
    }
  }

  return cls.join(' ');
});

defineExpose({
  handleReset,
  handleSubmit,
});
</script>
<template>
  <div :class="cn(actionWrapperClass)">
    <!-- 展开按钮前 -->
    <slot name="expand-before"></slot>

    <!-- 纯图标按钮，与「重置」用同一个按钮组件渲染，高度/圆角/描边/配色天然一致；
         箭头朝下 = 可展开，朝上 = 可收起 -->
    <component
      :is="COMPONENT_MAP.DefaultButton"
      v-if="showCollapse"
      :aria-expanded="!collapsed"
      :aria-label="collapsed ? $t('expand') : $t('collapse')"
      :title="collapsed ? $t('expand') : $t('collapse')"
      class="px-2"
      type="button"
      @click="collapsed = !collapsed"
    >
      <ChevronDown
        :class="
          cn('size-4 transition-transform duration-300', {
            'rotate-180': !collapsed,
          })
        "
      />
    </component>

    <!-- 展开按钮后 -->
    <slot name="expand-after"></slot>

    <template v-if="rootProps.actionButtonsReverse">
      <!-- 提交按钮前 -->
      <slot name="submit-before"></slot>

      <component
        :is="COMPONENT_MAP.PrimaryButton"
        v-if="submitButtonOptions.show"
        type="button"
        @click="handleSubmit"
        v-bind="submitButtonOptions"
      >
        {{ submitButtonOptions.content }}
      </component>
    </template>

    <!-- 重置按钮前 -->
    <slot name="reset-before"></slot>

    <component
      :is="COMPONENT_MAP.DefaultButton"
      v-if="resetButtonOptions.show"
      type="button"
      @click="handleReset"
      v-bind="resetButtonOptions"
    >
      {{ resetButtonOptions.content }}
    </component>

    <template v-if="!rootProps.actionButtonsReverse">
      <!-- 提交按钮前 -->
      <slot name="submit-before"></slot>

      <component
        :is="COMPONENT_MAP.PrimaryButton"
        v-if="submitButtonOptions.show"
        type="button"
        @click="handleSubmit"
        v-bind="submitButtonOptions"
      >
        {{ submitButtonOptions.content }}
      </component>
    </template>
  </div>
</template>





