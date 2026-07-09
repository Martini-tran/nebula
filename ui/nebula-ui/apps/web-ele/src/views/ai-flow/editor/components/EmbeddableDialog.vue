<script lang="ts" setup>
/**
 * 可嵌入弹窗外壳：让节点配置组件在「独立弹窗」与「常驻抽屉内嵌」两种形态间切换，
 * 而表单主体只写一份。
 *
 * - embedded=false（默认）：渲染 ElDialog（保留原右键菜单弹出行为）。
 * - embedded=true：渲染一个无外壳的容器，只输出表单体 + footer，供 NodeConfigPanel 内嵌。
 *
 * 用法：把各 ConfigDialog 里的 <ElDialog> 换成 <EmbeddableDialog :embedded>，
 * 默认插槽放表单体、#footer 插槽放按钮，visible 用 v-model 承接（embedded 时忽略）。
 */
import { ElDialog } from 'element-plus';

defineOptions({ name: 'EmbeddableDialog' });

withDefaults(
  defineProps<{
    /** 内嵌模式：true 去掉 ElDialog 外壳，只渲染表单体 + footer */
    embedded?: boolean;
    title?: string;
    width?: string;
    top?: string;
    dialogClass?: string;
  }>(),
  { embedded: false, width: '820px', top: '5vh', dialogClass: '' },
);

/** 弹窗可见性（仅非嵌入模式生效） */
const visible = defineModel<boolean>('visible', { default: false });
</script>

<template>
  <!-- 独立弹窗形态 -->
  <ElDialog
    v-if="!embedded"
    v-model="visible"
    append-to-body
    :class="dialogClass"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    destroy-on-close
    :title="title"
    :top="top"
    :width="width"
  >
    <slot></slot>
    <template #footer>
      <slot name="footer"></slot>
    </template>
  </ElDialog>

  <!-- 内嵌形态：无外壳，表单体 + footer 直接输出到抽屉 -->
  <div v-else class="embedded-body">
    <div class="embedded-form">
      <slot></slot>
    </div>
    <div class="embedded-footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<style scoped>
/**
 * 内嵌形态挂在 NodeConfigPanel 的 .panel-body（flex column）里，作为 flex item
 * 用 flex:1 + min-height:0 拿高度——不写 height:100%，否则祖先链上任一层是 auto
 * 高度时它就退化成内容高度，把 .embedded-form 的 overflow-y:auto 顶失效。
 */
.embedded-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.embedded-form {
  flex: 1;
  min-height: 0;
  padding: 4px 2px 8px;
  overflow-y: auto;
}

.embedded-footer {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 2px 2px;
  border-top: 1px solid var(--el-border-color-light);
}
</style>
