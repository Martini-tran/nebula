<script setup lang="ts">
import { computed } from 'vue'
import { Prompts, Welcome } from 'vue-element-plus-x'
import type { PromptsItemsProps } from 'vue-element-plus-x/types/Prompts'

const props = defineProps<{
  /** 关联作品的标题；为空表示通用对话 */
  workTitle?: string | null
}>()

const emit = defineEmits<{ ask: [question: string] }>()

/** 关联作品后换成针对这本书的问题：此时 AI 能读到它的设定（批次 C4 起真正生效） */
const items = computed<PromptsItemsProps[]>(() =>
  props.workTitle
    ? [
        { key: 'relation', label: '帮我梳理主要人物关系', description: `基于《${props.workTitle}》的设定库` },
        { key: 'conflict', label: '检查最近三章有没有前后矛盾', description: '对照锁定的硬事实' },
        { key: 'thread', label: '还有哪些伏笔没有回收', description: '按埋设章节列出' },
        { key: 'next', label: '下一章可以怎么推进', description: '结合当前梗概给出三个方向' },
      ]
    : [
        { key: 'opening', label: '怎么写一个抓人的开头？', description: '开篇三段该放什么' },
        { key: 'character', label: '帮我立一个有记忆点的人物', description: '习惯、欲望与冲突' },
        { key: 'foreshadow', label: '伏笔怎么埋才不突兀？', description: '埋设与回收的节奏' },
        { key: 'stuck', label: '卡文了，写不下去怎么办', description: '先找到下一场戏的目标' },
      ],
)

const onClick = (item: PromptsItemsProps) => {
  if (item.label) emit('ask', item.label)
}
</script>

<template>
  <section class="welcome">
    <Welcome
      variant="borderless"
      :title="workTitle ? `聊聊《${workTitle}》` : '今天想聊点什么？'"
      :description="
        workTitle
          ? '我会结合这本书的设定、编年和台词样本来回答。'
          : '情节、人物、伏笔、卡文——直接问。关联一本作品后，我能读到它的设定。'
      "
    />
    <Prompts :items="items" wrap class="welcome__prompts" @item-click="onClick" />
  </section>
</template>

<style scoped lang="scss">
.welcome {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
  padding-top: 8vh;
}

.welcome__prompts :deep(.elx-prompts__items) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.7rem;
}

.welcome__prompts :deep(.elx-prompts__item) {
  width: auto;
  margin: 0;
}

@media (max-width: 640px) {
  .welcome__prompts :deep(.elx-prompts__items) {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
