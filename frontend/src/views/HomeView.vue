<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Activity,
  ArrowRight,
  BookOpen,
  Clock,
  Copy,
  Loader2,
  Search,
  Sparkles,
} from 'lucide-vue-next'
import { api, logEvent } from '../api/client'
import { ensureBooks, ensureStats, store, toast } from '../store'
import { splitParentheses, timeAgo } from '../utils/format'
import { formatDateLabel, pickDailyVerse } from '../utils/daily'

const route = useRoute()
const router = useRouter()
const query = ref('')
const searching = ref(false)

const today = ref(new Date())
const dateLabel = computed(() => formatDateLabel(today.value))
const daily = ref(null)
const dailyLoading = ref(true)
const dailyError = ref(false)
let dailyTimer = null

const otGroups = computed(() => groupBooks('OT'))
const ntGroups = computed(() => groupBooks('NT'))

const continueTarget = computed(() => {
  if (store.lastRead?.bookId) return `/read/${store.lastRead.bookId}/${store.lastRead.chapter}`
  return '/read/1/1'
})

const stats = computed(() => store.stats || { books: 66, chapters: 1189, verses: 31102 })

const history = computed(() => store.history.slice(0, 6))

function groupBooks(testament) {
  const map = new Map()
  for (const book of store.books) {
    if (book.testament !== testament) continue
    if (!map.has(book.category)) map.set(book.category, [])
    map.get(book.category).push(book)
  }
  return [...map.entries()].map(([category, books]) => ({ category, books }))
}

function cleanupDailyCache(currentKey) {
  try {
    const expired = []
    for (let i = 0; i < localStorage.length; i += 1) {
      const key = localStorage.key(i)
      if (key && key.startsWith('bible:daily:v2:') && key !== currentKey) {
        expired.push(key)
      }
    }
    expired.forEach((key) => localStorage.removeItem(key))
  } catch {
    /* 本地存储不可用时忽略 */
  }
}

async function loadDaily(force = false) {
  const now = new Date()
  today.value = now
  // ?daily=序号（从 1 起）可预览金句库中的指定条目，便于查看多节经文效果
  const preview = Number(route.query.daily)
  const picked = pickDailyVerse(now, Number.isInteger(preview) && preview > 0 ? preview - 1 : undefined)
  const startVerse = picked.ref.verse
  const endVerse = picked.ref.verseEnd || startVerse
  const cacheKey = `bible:daily:v2:${picked.dateKey}:${picked.ref.bookId}:${picked.ref.chapter}:${startVerse}-${endVerse}`
  dailyLoading.value = true
  dailyError.value = false
  try {
    if (!force) {
      let cached = null
      try {
        cached = localStorage.getItem(cacheKey)
      } catch {
        /* 隐私模式下忽略本地缓存 */
      }
      if (cached) {
        try {
          daily.value = JSON.parse(cached)
          cleanupDailyCache(cacheKey)
          return
        } catch {
          /* 缓存损坏则重新请求 */
        }
      }
    }
    const chapter = await api.getChapter(picked.ref.bookId, picked.ref.chapter)
    // 同一章内按 verse ~ verseEnd 连续取节，拼成一段展示
    const verses = chapter.verses.filter((item) => item.verse >= startVerse && item.verse <= endVerse)
    if (!verses.length) throw new Error('今日经文不存在')
    daily.value = {
      ...picked.ref,
      verseEnd: endVerse > startVerse ? endVerse : undefined,
      bookName: chapter.book.name,
      text: verses.map((item) => item.text).join(''),
      dateKey: picked.dateKey,
    }
    try {
      localStorage.setItem(cacheKey, JSON.stringify(daily.value))
    } catch {
      /* 存储失败不影响到展示 */
    }
    cleanupDailyCache(cacheKey)
  } catch {
    dailyError.value = true
    // 网络异常时保留一张可用的备用金句，网络恢复后下一次整点/刷新会重新获取
    daily.value = {
      bookId: 19,
      chapter: 119,
      verse: 105,
      bookName: '诗篇',
      text: '你的话是我脚前的灯，是我路上的光。',
      tag: '引导',
      dateKey: picked.dateKey,
    }
  } finally {
    dailyLoading.value = false
  }
}

async function submitSearch(raw = query.value) {
  const q = String(raw || '').trim()
  if (!q || searching.value) return
  searching.value = true
  try {
    const reference = await api.parseReference(q)
    const range = reference.verseEnd > reference.verseStart
      ? `${reference.verseStart}-${reference.verseEnd}`
      : String(reference.verseStart || '')
    router.push({
      name: 'read',
      params: { bookId: reference.bookId, chapter: reference.chapter },
      query: range ? { verse: range } : {},
    })
    logEvent('jump', {
      message: `首页跳转 ${reference.display || q}`,
      bookId: reference.bookId,
      chapter: reference.chapter,
      verse: reference.verseStart,
      query: q,
    })
  } catch (error) {
    if (error.status === 400) {
      router.push({ name: 'search', query: { q } })
      logEvent('search', { message: `首页搜索 ${q}`, query: q })
    } else {
      toast(error.message || '搜索服务暂不可用', 'error')
    }
  } finally {
    searching.value = false
  }
}

function quickSearch(value) {
  query.value = value
  submitSearch(value)
}

function openDaily() {
  if (!daily.value) return
  router.push({
    name: 'read',
    params: { bookId: daily.value.bookId, chapter: daily.value.chapter },
    query: {
      verse: daily.value.verseEnd
        ? `${daily.value.verse}-${daily.value.verseEnd}`
        : String(daily.value.verse),
    },
  })
}

onMounted(() => {
  ensureBooks().catch(() => {})
  ensureStats()
  loadDaily()
  logEvent('page_view', { message: '访问首页' })
  // 跨过零点后自动刷新今日经文与日期
  dailyTimer = window.setInterval(() => {
    const now = new Date()
    if (pickDailyVerse(now).dateKey !== daily.value?.dateKey) {
      loadDaily(true)
    } else {
      today.value = now
    }
  }, 60000)
})

onUnmounted(() => {
  if (dailyTimer) window.clearInterval(dailyTimer)
})
</script>

<template>
  <div class="home">
    <section class="home-hero">
      <div class="hero-glow hero-glow-a" aria-hidden="true" />
      <div class="hero-glow hero-glow-b" aria-hidden="true" />
      <div class="hero-grid-lines" aria-hidden="true" />
      <div class="home-container hero-inner">
        <div class="hero-copy">
          <p class="hero-eyebrow"><span /> 和合本 · 精读圣经</p>
          <h1 class="hero-title">
            你的话是我脚前的灯，<br />
            <em>是我路上的光。</em>
          </h1>
          <p class="hero-ref">诗篇 119:105</p>
          <p class="hero-desc">
            六十六卷，一千一百八十九章，三万一千一百零二节。
            在安静中阅读，在经文中停留。
          </p>

          <form class="hero-search" role="search" @submit.prevent="submitSearch()">
            <Search :size="20" :stroke-width="1.7" />
            <input
              v-model="query"
              type="search"
              placeholder="搜索经文，或输入 约3:16 直接跳转"
              aria-label="搜索经文或输入经文引用"
              autocomplete="off"
              spellcheck="false"
            />
            <button type="submit" class="hero-search-btn" :disabled="searching">
              <Loader2 v-if="searching" class="spin" :size="18" />
              <template v-else>
                搜索
                <ArrowRight :size="16" />
              </template>
            </button>
          </form>

          <div class="hero-chips" aria-label="搜索示例">
            <span>试试</span>
            <button type="button" @click="quickSearch('神爱世人')">神爱世人</button>
            <button type="button" @click="quickSearch('约3:16')">约3:16</button>
            <button type="button" @click="quickSearch('诗23篇')">诗23篇</button>
            <button type="button" @click="quickSearch('复活')">复活</button>
          </div>

          <div class="hero-stats">
            <div><strong>{{ stats.books }}</strong><span>卷</span></div>
            <i />
            <div><strong>{{ stats.chapters.toLocaleString() }}</strong><span>章</span></div>
            <i />
            <div><strong>{{ stats.verses.toLocaleString() }}</strong><span>节</span></div>
          </div>
        </div>

        <aside class="daily-card">
          <div class="daily-head">
            <span><Sparkles :size="14" /> 今日经文 <em>热门金句</em></span>
            <time>{{ dateLabel }}</time>
          </div>
          <div class="daily-body">
            <span class="daily-quote" aria-hidden="true">“</span>
            <template v-if="dailyLoading">
              <div class="daily-skeleton">
                <span /><span /><span />
              </div>
            </template>
            <template v-else-if="daily">
              <p class="daily-text"><template v-for="(part, partIndex) in splitParentheses(daily.text)" :key="partIndex"><span v-if="part.paren" class="daily-paren">{{ part.text }}</span><template v-else>{{ part.text }}</template></template></p>
              <div class="daily-meta">
                <p class="daily-ref">{{ daily.bookName }} {{ daily.chapter }}:{{ daily.verse }}<template v-if="daily.verseEnd">-{{ daily.verseEnd }}</template></p>
                <span v-if="daily.tag" class="daily-tag">{{ daily.tag }}</span>
              </div>
            </template>
            <template v-else>
              <p class="daily-text">你的话是我脚前的灯，是我路上的光。</p>
              <p class="daily-ref">诗篇 119:105</p>
            </template>
          </div>
          <button v-if="daily" type="button" class="daily-link" @click="openDaily">
            阅读本章
            <ArrowRight :size="15" />
          </button>
          <p class="daily-note">
            <template v-if="dailyError">网络暂不可用 · 显示备用金句</template>
            <template v-else>热门金句库 · 每日更新</template>
          </p>
        </aside>
      </div>
    </section>

    <div class="home-container home-content">
      <section class="continue-card">
        <div class="continue-main">
          <p class="section-eyebrow"><Clock :size="14" /> 继续阅读</p>
          <template v-if="store.lastRead">
            <h2>{{ store.lastRead.bookName }} <span>第 {{ store.lastRead.chapter }} 章</span></h2>
            <p class="continue-meta">上次阅读于 {{ timeAgo(store.lastRead.at) }}</p>
          </template>
          <template v-else>
            <h2>从创世记开始 <span>第 1 章</span></h2>
            <p class="continue-meta">起初，神创造天地。</p>
          </template>
          <RouterLink :to="continueTarget" class="btn primary">
            继续阅读
            <ArrowRight :size="16" />
          </RouterLink>
        </div>

        <div v-if="history.length" class="continue-history">
          <p>最近读过</p>
          <div class="history-links">
            <RouterLink v-for="item in history" :key="`${item.bookId}-${item.chapter}`" :to="`/read/${item.bookId}/${item.chapter}`">
              {{ item.bookShortName }} {{ item.chapter }}
            </RouterLink>
          </div>
        </div>
        <div v-else class="continue-history">
          <p>读经提示</p>
          <p class="continue-tip">点击任意经文可选中并复制，按住 Shift 可连续选择多节。</p>
        </div>

        <div class="continue-stats">
          <div><strong>{{ stats.books }}</strong><span>书卷</span></div>
          <div><strong>{{ stats.chapters.toLocaleString() }}</strong><span>章</span></div>
          <div><strong>{{ stats.verses.toLocaleString() }}</strong><span>节</span></div>
        </div>
      </section>

      <section class="canon-section">
        <header class="section-head">
          <div>
            <p class="section-eyebrow">THE CANON</p>
            <h2>六十六卷</h2>
          </div>
          <p class="section-note">旧约 39 卷 · 新约 27 卷 · 点击进入阅读</p>
        </header>

        <div v-if="store.booksLoading && !store.books.length" class="canon-loading">
          <Loader2 class="spin" :size="20" /> 正在载入书卷……
        </div>
        <div v-else-if="store.booksError && !store.books.length" class="canon-error">
          <p>{{ store.booksError }}</p>
          <button type="button" class="btn ghost" @click="ensureBooks(true)">重试</button>
        </div>
        <div v-else class="canon-grid">
          <div class="canon-column">
            <div class="canon-column-head">
              <span>旧约</span>
              <b>39</b>
              <i>OLD TESTAMENT</i>
            </div>
            <div v-for="group in otGroups" :key="group.category" class="canon-group">
              <h3>{{ group.category }}</h3>
              <div class="canon-books">
                <RouterLink v-for="book in group.books" :key="book.id" :to="`/read/${book.id}/1`" class="book-chip">
                  <span class="book-chip-name">{{ book.name }}</span>
                  <span class="book-chip-count">{{ book.chapterCount }}</span>
                </RouterLink>
              </div>
            </div>
          </div>

          <div class="canon-column">
            <div class="canon-column-head">
              <span>新约</span>
              <b>27</b>
              <i>NEW TESTAMENT</i>
            </div>
            <div v-for="group in ntGroups" :key="group.category" class="canon-group">
              <h3>{{ group.category }}</h3>
              <div class="canon-books">
                <RouterLink v-for="book in group.books" :key="book.id" :to="`/read/${book.id}/1`" class="book-chip">
                  <span class="book-chip-name">{{ book.name }}</span>
                  <span class="book-chip-count">{{ book.chapterCount }}</span>
                </RouterLink>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="feature-strip">
        <article class="feature-card">
          <span class="feature-index">01</span>
          <Search :size="22" :stroke-width="1.5" />
          <h3>全文搜索</h3>
          <p>支持多关键词组合与经文引用识别，三万一千余节经文快速定位。</p>
        </article>
        <article class="feature-card">
          <span class="feature-index">02</span>
          <Copy :size="22" :stroke-width="1.5" />
          <h3>选择复制</h3>
          <p>点选经文，Shift 连续选择，一键复制带书卷章节的引用格式。</p>
        </article>
        <article class="feature-card">
          <span class="feature-index">03</span>
          <Activity :size="22" :stroke-width="1.5" />
          <h3>日志检索</h3>
          <p>Elasticsearch 驱动的阅读行为与接口日志，随时回溯使用轨迹。</p>
        </article>
      </section>

      <section class="home-closing">
        <BookOpen :size="22" :stroke-width="1.4" />
        <p>你的言语一解开，就发出亮光，使愚人通达。</p>
        <span>诗篇 119:130</span>
      </section>
    </div>
  </div>
</template>
