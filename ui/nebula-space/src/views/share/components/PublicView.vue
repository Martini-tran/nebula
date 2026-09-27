<script setup lang="ts">
/**
 * 访客看到的公开主页：比工作台更像个人网站——头像与一句话、外部链接、Now、书签合集、最近读完。
 * 公开页和设置页的「以访客身份预览」共用这一个组件。
 */
import { ref } from 'vue'
import { Icon } from '@iconify/vue'
import { favColor } from '../../../api/search'
import { monthDay } from '../../../utils/date'
import type { PublicPage } from '../../../types/profile'

defineProps<{ page: PublicPage; preview?: boolean; importing?: string | null }>()
const emit = defineEmits<{ import: [collectionId: string] }>()

const revealed = ref(new Set<string>())
const reveal = (url: string) => (revealed.value = new Set(revealed.value).add(url))
const open = ref<string | null>(null)
const emailOf = (url: string) => url.replace(/^mailto:/i, '')
</script>

<template>
  <div class="pub">
    <header v-if="page.blocks.includes('intro')" class="me">
      <span class="me__av">{{ page.nickname.charAt(0).toUpperCase() }}</span>
      <div>
        <h1>{{ page.nickname }}</h1>
        <p v-if="page.bio">{{ page.bio }}</p>
      </div>
    </header>

    <div v-if="page.links.length" class="links">
      <template v-for="l in page.links" :key="l.url">
        <button v-if="l.masked && !revealed.has(l.url)" type="button" @click="reveal(l.url)"><Icon icon="lucide:mail" />{{ l.label }}（点击显示）</button>
        <a v-else-if="l.masked" :href="`mailto:${emailOf(l.url)}`"><Icon icon="lucide:mail" />{{ emailOf(l.url) }}</a>
        <a v-else :href="l.url" target="_blank" rel="noopener noreferrer me"><Icon icon="lucide:link" />{{ l.label }}</a>
      </template>
    </div>

    <template v-for="block in page.blocks" :key="block">
      <section v-if="block === 'now' && page.now.trim()" class="now">
        <h2>NOW <span v-if="page.nowUpdated">{{ monthDay(page.nowUpdated) }}更新</span></h2>
        <ul><li v-for="(line, i) in page.now.split('\n').filter((l) => l.trim())" :key="i">{{ line.replace(/^[-*]\s*/, '') }}</li></ul>
      </section>

      <section v-else-if="block === 'collections' && page.collections.length" class="sec">
        <h2>书签合集 <span>{{ page.collections.length }}</span></h2>
        <div class="colls">
          <article v-for="c in page.collections" :key="c.id" class="coll" :class="{ open: open === c.id }">
            <button type="button" class="coll__head" :aria-expanded="open === c.id" @click="open = open === c.id ? null : c.id">
              <b>{{ c.title }}</b>
              <p v-if="c.description">{{ c.description }}</p>
              <span class="coll__favs">
                <span v-for="b in c.bookmarks.slice(0, 5)" :key="b.url" class="fav" :style="{ background: favColor(b.domain) }">{{ (b.title || b.domain).charAt(0).toUpperCase() }}</span>
              </span>
            </button>
            <ul v-if="open === c.id" class="coll__list">
              <li v-for="b in c.bookmarks" :key="b.url">
                <a :href="b.url" target="_blank" rel="noopener noreferrer">{{ b.title }}</a><small>{{ b.domain }}</small>
              </li>
              <li v-if="!c.bookmarks.length" class="empty">这个合集还是空的</li>
            </ul>
            <div class="coll__foot">
              <span>{{ c.bookmarks.length }} 个<template v-if="c.updated"> · 更新于 {{ Number(c.updated.slice(5, 7)) }}/{{ Number(c.updated.slice(8)) }}</template></span>
              <button type="button" :disabled="preview || importing === c.id" @click="emit('import', c.id)">
                {{ importing === c.id ? '导入中…' : '导入到我的 Space' }}
              </button>
            </div>
          </article>
        </div>
      </section>

      <section v-else-if="block === 'reading' && page.reading.length" class="sec">
        <h2>最近读完</h2>
        <div class="reads">
          <a v-for="r in page.reading" :key="r.url" :href="r.url" target="_blank" rel="noopener noreferrer" class="read">
            <span class="fav" :style="{ background: favColor(r.domain) }">{{ r.domain.charAt(0).toUpperCase() }}</span>
            <span>{{ r.title }}</span>
            <small>{{ r.date ? monthDay(r.date) : '' }}</small>
          </a>
        </div>
      </section>

      <section v-else-if="block === 'goals' && page.goals.length" class="sec">
        <h2>今年的目标</h2>
        <div class="goals">
          <div v-for="g in page.goals" :key="g.title" class="goal">
            <span>{{ g.icon }} {{ g.title }}</span>
            <span class="bar"><i :style="{ width: `${Math.round(g.pct * 100)}%` }" /></span>
            <small>{{ Math.round(g.pct * 100) }}%</small>
          </div>
        </div>
      </section>

      <section v-else-if="block === 'quotes' && page.quotes.length" class="sec">
        <h2>摘录</h2>
        <blockquote v-for="(q, i) in page.quotes" :key="i" class="quote">{{ q.text }}<small v-if="q.source">—— {{ q.source }}</small></blockquote>
      </section>
    </template>

    <footer class="foot">用 Nebula Space 搭建 · 本页只包含作者主动公开的内容</footer>
  </div>
</template>

<style scoped>
.pub {
  display: flex;
  flex-direction: column;
  gap: 1.6rem;
  width: min(44rem, 100%);
  margin: 0 auto;
  padding: 2.5rem 1.25rem 2rem;
}

.me {
  display: flex;
  align-items: center;
  gap: 1.1rem;
}

.me__av {
  display: grid;
  place-items: center;
  flex: none;
  width: 4.2rem;
  height: 4.2rem;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-brand), var(--color-accent));
  color: #fff;
  font-size: 1.7rem;
  font-weight: 800;
}

.me h1 {
  font-size: 1.6rem;
  font-weight: 800;
}

.me p {
  margin-top: 0.2rem;
  font-size: 0.95rem;
  line-height: 1.7;
  color: var(--color-text-secondary);
}

.links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: -0.6rem;
}

.links a,
.links button {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.3rem 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: inherit;
  font-size: 0.84rem;
  cursor: pointer;
}

.links a:hover,
.links button:hover {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

h2 {
  margin-bottom: 0.7rem;
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: var(--color-text-secondary);
}

h2 span {
  font-weight: 500;
  letter-spacing: 0;
}

.now {
  padding: 1rem 1.2rem;
  border-left: 3px solid var(--color-accent);
  border-radius: 0 var(--radius-lg) var(--radius-lg) 0;
  background: var(--color-accent-soft);
}

.now ul {
  margin: 0;
  padding-left: 1.1rem;
  line-height: 1.9;
}

.colls {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(13rem, 1fr));
  gap: 0.75rem;
}

.coll {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
}

.coll.open {
  grid-column: 1 / -1;
}

.coll__head {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.9rem 1rem 0.5rem;
  border: 0;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.coll__head p {
  font-size: 0.82rem;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.coll__favs {
  display: flex;
  gap: 0.25rem;
  margin-top: 0.2rem;
}

.fav {
  display: grid;
  place-items: center;
  flex: none;
  width: 1.4rem;
  height: 1.4rem;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 0.66rem;
  font-weight: 800;
}

.coll__list {
  margin: 0;
  padding: 0.2rem 1rem 0.5rem;
  list-style: none;
}

.coll__list li {
  display: flex;
  gap: 0.6rem;
  align-items: baseline;
  padding: 0.3rem 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.86rem;
}

.coll__list a:hover {
  color: var(--color-brand);
}

.coll__list small {
  margin-left: auto;
  font-size: 0.74rem;
  color: var(--color-text-secondary);
}

.coll__list .empty {
  color: var(--color-text-secondary);
}

.coll__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem 0.8rem;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding: 0.5rem 1rem 0.8rem;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.coll__foot button {
  white-space: nowrap;
  border: 0;
  background: none;
  color: var(--color-brand);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
}

.coll__foot button:disabled {
  opacity: 0.55;
  cursor: default;
}

.reads {
  display: flex;
  flex-direction: column;
}

.read {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0.2rem;
  border-bottom: 1px solid var(--color-border);
  font-size: 0.9rem;
  color: inherit;
}

.read:hover span:nth-child(2) {
  color: var(--color-brand);
}

.read small {
  margin-left: auto;
  font-size: 0.76rem;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.goals {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.goal {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 8rem 2.5rem;
  align-items: center;
  gap: 0.7rem;
  font-size: 0.88rem;
}

.bar {
  height: 0.4rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-bg-soft);
}

.bar i {
  display: block;
  height: 100%;
  background: var(--color-accent);
}

.goal small {
  font-size: 0.76rem;
  text-align: right;
  color: var(--color-text-secondary);
}

.quote {
  margin: 0 0 0.6rem;
  padding: 0.6rem 0.9rem;
  border-left: 3px solid #eab308;
  font-family: Georgia, 'Noto Serif SC', 'Songti SC', serif;
  line-height: 1.8;
}

.quote small {
  display: block;
  font-family: var(--font-sans);
  font-size: 0.76rem;
  color: var(--color-text-secondary);
}

.foot {
  padding-top: 1.2rem;
  border-top: 1px solid var(--color-border);
  font-size: 0.76rem;
  text-align: center;
  color: var(--color-text-secondary);
}
</style>
