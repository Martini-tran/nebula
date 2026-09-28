<script setup lang="ts">
/**
 * AI 重排目录：AI 看整棵目录树（名称、书签数、几条标题）给出新建 / 改名 / 移动 / 合并的方案，
 * 后端已按当前目录树逐条校验过。用户逐条勾选后按顺序执行，每条用现有接口完成。
 * 方案里新建的目录用 N1、N2… 编号，后面的操作可能引用它；没勾选那条新建时，引用它的操作也不能执行。
 * 不是事务：某条失败就标出来，接着执行其余的。
 */
import { computed, ref } from 'vue'
import { Icon } from '@iconify/vue'
import {
  createFolder,
  deleteFolder,
  fetchAllBookmarks,
  moveBookmarks,
  moveFolder,
  planFolders,
  updateFolder,
} from '../../../api/space'
import { useSpaceStore } from '../../../stores/space'
import { errorText, toast } from '../../../composables/useToast'
import type { EntityId, FolderPlan, FolderPlanOp } from '../../../types/space'

interface OpState {
  op: FolderPlanOp
  on: boolean
  status: 'pending' | 'running' | 'done' | 'failed' | 'skipped'
  message?: string
}

const space = useSpaceStore()

const hint = ref('')
const loading = ref(false)
const error = ref('')
const plan = ref<FolderPlan | null>(null)
const ops = ref<OpState[]>([])

const generate = async () => {
  loading.value = true
  error.value = ''
  try {
    await space.reload()
    plan.value = await planFolders(hint.value.trim())
    ops.value = (plan.value?.ops ?? []).map((op) => ({ op, on: true, status: 'pending' }))
  } catch (err) {
    error.value = errorText(err, '生成方案失败')
  } finally {
    loading.value = false
  }
}

const isNewKey = (ref?: string | null) => Boolean(ref && /^N\d+$/.test(ref))

/** 勾选了的新建目录编号 */
const createdOn = computed(
  () => new Set(ops.value.filter((s) => s.op.op === 'create' && s.on && s.status !== 'failed').map((s) => s.op.key)),
)

/** 引用的新目录没勾选（或没建成） */
const blocked = (s: OpState) =>
  [s.op.folder, s.op.parent, s.op.into].some((ref) => isNewKey(ref) && !createdOn.value.has(ref!))

const runnable = computed(() => ops.value.filter((s) => s.on && !blocked(s) && s.status === 'pending'))

const ICON: Record<FolderPlanOp['op'], string> = {
  create: 'lucide:folder-plus',
  rename: 'lucide:pencil',
  move: 'lucide:folder-input',
  merge: 'lucide:merge',
}

const LABEL: Record<FolderPlanOp['op'], string> = { create: '新建', rename: '改名', move: '移动', merge: '合并' }

// ── 执行 ──

const applying = ref(false)

const apply = async () => {
  if (applying.value || !runnable.value.length) return
  applying.value = true
  const created = new Map<string, EntityId>()
  /** 解析引用：顶层 → 0，新目录编号 → 建好后的 ID；编号对应的目录没建成时返回 undefined */
  const resolve = (ref?: string | null): EntityId | undefined => {
    if (!ref || ref === '0') return 0
    return isNewKey(ref) ? created.get(ref) : ref
  }
  let done = 0
  let failed = 0
  for (const s of ops.value) {
    if (!s.on || s.status !== 'pending') continue
    const refs = [s.op.folder, s.op.parent, s.op.into].filter(isNewKey)
    if (refs.some((ref) => !created.has(ref!))) {
      s.status = 'skipped'
      s.message = '它依赖的新目录没有建成'
      continue
    }
    s.status = 'running'
    try {
      const { op } = s
      if (op.op === 'create') {
        created.set(op.key!, await createFolder({ parentId: resolve(op.parent)!, name: op.name! }))
      } else if (op.op === 'rename') {
        await updateFolder(resolve(op.folder)!, { name: op.name! })
      } else if (op.op === 'move') {
        await moveFolder(resolve(op.folder)!, resolve(op.parent)!)
      } else {
        // 合并：书签（任意状态）搬过去 → 子目录搬过去 → 删掉空目录
        const from = resolve(op.folder)!
        const into = resolve(op.into)!
        const ids = (await fetchAllBookmarks({ folderId: from })).map((b) => b.id)
        if (ids.length) await moveBookmarks(ids, into)
        await space.reload()
        for (const child of space.findFolder(from)?.children ?? []) await moveFolder(child.id, into)
        await deleteFolder(from)
      }
      s.status = 'done'
      done += 1
    } catch (err) {
      s.status = 'failed'
      s.message = errorText(err, '执行失败')
      failed += 1
    }
    await space.reload()
  }
  applying.value = false
  if (failed) toast.error(`${done} 项已完成，${failed} 项失败`)
  else toast.ok(`目录已调整，完成 ${done} 项`)
}

const finished = computed(() => ops.value.length > 0 && ops.value.every((s) => s.status !== 'pending' || !s.on || blocked(s)))
const anyDone = computed(() => ops.value.some((s) => s.status === 'done'))
</script>

<template>
  <div class="af">
    <section class="af__ask surface">
      <label class="field">
        <span class="field__label">整理要求（可选）</span>
        <input
          v-model="hint"
          class="field__input"
          maxlength="200"
          placeholder="例如：按技术方向分类，最多两层"
          :disabled="loading || applying"
          @keydown.enter.prevent="generate"
        />
      </label>
      <button class="btn btn--primary" type="button" :disabled="loading || applying || !space.flat.length" @click="generate">
        <Icon :icon="loading ? 'lucide:loader-circle' : 'lucide:sparkles'" :class="{ spin: loading }" />
        {{ loading ? 'AI 正在看目录…' : plan ? '重新生成' : '生成方案' }}
      </button>
    </section>

    <p v-if="!space.flat.length" class="af__muted">还没有目录，先建几个目录或导入书签再来整理。</p>
    <p v-if="error" class="form__error">{{ error }}</p>

    <section v-if="plan && !loading" class="af__plan">
      <p v-if="plan.summary" class="af__summary"><Icon icon="lucide:sparkles" />{{ plan.summary }}</p>

      <template v-if="ops.length">
        <ol class="af__ops">
          <li
            v-for="(s, i) in ops"
            :key="i"
            class="op surface"
            :class="[`op--${s.status}`, { 'op--off': !s.on || blocked(s) }]"
          >
            <label class="op__check">
              <input
                v-model="s.on"
                type="checkbox"
                :disabled="applying || s.status !== 'pending' || blocked(s)"
                :aria-label="`采纳第 ${i + 1} 项`"
              />
            </label>
            <Icon :icon="ICON[s.op.op]" class="op__icon" />
            <div class="op__body">
              <p class="op__text">
                <span class="op__kind">{{ LABEL[s.op.op] }}</span>
                <template v-if="s.op.op === 'create'">「{{ s.op.after }}」</template>
                <template v-else-if="s.op.op === 'rename'">「{{ s.op.before }}」改名为「{{ s.op.name }}」</template>
                <template v-else-if="s.op.op === 'move'">「{{ s.op.before }}」→「{{ s.op.after }}」</template>
                <template v-else>
                  「{{ s.op.before }}」并入「{{ s.op.after }}」
                  <small>（{{ s.op.bookmarkCount ?? 0 }} 条书签和子目录一起过去，原目录删除）</small>
                </template>
              </p>
              <p v-if="s.op.reason" class="op__reason">{{ s.op.reason }}</p>
              <p v-if="blocked(s) && s.status === 'pending'" class="op__note">依赖的新建目录没有勾选</p>
              <p v-if="s.message" class="op__note">{{ s.message }}</p>
            </div>
            <span class="op__status">
              <Icon v-if="s.status === 'running'" icon="lucide:loader-circle" class="spin" />
              <Icon v-else-if="s.status === 'done'" icon="lucide:check" />
              <Icon v-else-if="s.status === 'failed'" icon="lucide:circle-x" />
            </span>
          </li>
        </ol>
        <p v-if="plan.dropped" class="af__muted">另有 {{ plan.dropped }} 条建议与现有目录对不上（比如同级重名），已略过。</p>
        <div class="af__foot">
          <router-link v-if="anyDone" to="/bookmarks/organize" class="btn btn--ghost">查看目录</router-link>
          <button class="btn btn--primary" type="button" :disabled="applying || !runnable.length" @click="apply">
            <Icon :icon="applying ? 'lucide:loader-circle' : 'lucide:check'" :class="{ spin: applying }" />
            {{ applying ? '执行中…' : finished && anyDone ? '已执行' : `执行选中的 ${runnable.length} 项` }}
          </button>
        </div>
      </template>
      <p v-else class="af__muted af__none">
        <Icon icon="lucide:circle-check" />目录结构已经比较合理，没有要调整的。<template v-if="plan.dropped">（另有 {{ plan.dropped }} 条建议不合规，已略过）</template>
      </p>
    </section>
  </div>
</template>

<style scoped>
.af {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.af__ask {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.75rem;
  padding: 1.1rem 1.25rem;
  border-radius: var(--radius-lg);
}

.af__ask .field {
  flex: 1;
  min-width: 14rem;
}

.af__muted {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.86rem;
  color: var(--color-text-secondary);
}

.af__none {
  justify-content: center;
  padding: 2rem;
}

.af__plan {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.af__summary {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.7rem 0.9rem;
  border-radius: var(--radius-lg);
  background: var(--color-brand-soft);
  font-size: 0.9rem;
  line-height: 1.65;
}

.af__summary svg {
  flex: none;
  margin-top: 0.25rem;
  color: var(--color-brand);
}

.af__ops {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.op {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  padding: 0.7rem 0.9rem;
  border-radius: var(--radius-lg);
}

.op--off {
  opacity: 0.55;
}

.op--done {
  border-color: color-mix(in srgb, var(--color-brand) 40%, var(--color-border));
}

.op--failed {
  border-color: var(--color-danger);
}

.op__check {
  display: inline-flex;
  padding-top: 0.2rem;
}

.op__check input {
  accent-color: var(--color-brand);
}

.op__icon {
  flex: none;
  width: 1.05rem;
  height: 1.05rem;
  margin-top: 0.2rem;
  color: var(--color-brand);
}

.op__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.op__text {
  font-size: 0.9rem;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.op__text small {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.op__kind {
  margin-right: 0.2rem;
  font-weight: 700;
}

.op__reason {
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.op__note {
  font-size: 0.8rem;
  color: var(--color-danger);
}

.op--skipped .op__note {
  color: var(--color-text-secondary);
}

.op__status {
  flex: none;
  width: 1.1rem;
  padding-top: 0.2rem;
  color: var(--color-brand);
}

.op--failed .op__status {
  color: var(--color-danger);
}

.af__foot {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
}
</style>
