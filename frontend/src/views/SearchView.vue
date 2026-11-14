<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  Copy,
  Loader2,
  Search,
  Sparkles,
  X,
} from 'lucide-vue-next'
import { api, logEvent } from '../api/client'
import { addSearchHistory, clearSearchHistory, ensureBooks, store, toast } from '../store'
import { buildCopyText, copyText, splitHighlights } from '../utils/format'

const route = useRoute()
const router = useRouter()

const input = ref('')
const scope = ref('')
const page = ref(1)
const data = ref(null)
const loading = ref(false)
const error = ref('')
const inputEl = ref(null)
const searchToken = ref(0)

const totalPages = computed(() => {
  if (!data.value?.total) return 0
  return Math.max(1, Math.ceil(data.value.total / (data.value.size || 20)))
})

const pageNumbers = computed(() => {
  const total = totalPages.value
  const current = page.value
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const set = new Set([1, total, current, current - 1, current + 1])
  if (current <= 3) [2, 3, 4].forEach((n) => set.add(n))
  if (current >= total - 2) [total - 1, total - 2, total - 3].forEach((n) => set.add(n))
  const sorted = [...set].filter((n) => n >= 1 && n <= total).sort((a, b) => a - b)
  const result = []
  let prev = 0
  for (const n of sorted) {
    if (prev && n - prev > 1) result.push('…')
    result.push(n)
    prev = n
  }
  return result
})

const examples = [
  { label: '神爱世人', hint: '主题搜索' },
  { label: '约3:16', hint: '引用跳转' },
  { label: '诗23篇', hint: '整章定位' },
  { label: '复活', hint: '关键词' },
]

const scopeLabel = computed(() => {
  if (!scope.value) return '全本圣经'
  const book = store.books.find((item) => Number(item.id) === Number(scope.value))
  return book ? book.name : '全本圣经'
})

function buildQuery(q, pageNumber = 1, bookId = scope.value) {
  const query = { q }
  if (bookId) query.book = String(bookId)
  if (pageNumber > 1) query.page = String(pageNumber)
  return query
}

async function fetchResults(q, pageNumber, bookId) {
  const token = ++searchToken.value
  loading.value = true
  error.value = ''
  try {
    const params = { q, page: pageNumber, size: 20 }
    if (bookId) params.bookId = bookId
    const result = await api.search(params)
    if (token !== searchToken.value) return
    data.value = result
    page.value = pageNumber
    if (pageNumber === 1) addSearchHistory(q)
    logEvent('search', {
      message: `搜索「${q}」`,
      query: q,
      detail: `${result.total} 条结果，${result.took}ms`,
    })
  } catch (err) {
    if (token !== searchToken.value) return
    error.value = err.message || '搜索失败'
    data.value = null
  } finally {
    if (token === searchToken.value) loading.value = false
  }
}

function applyRoute() {
  const q = String(route.query.q || '').trim()
  input.value = q
  scope.value = route.query.book ? String(route.query.book) : ''
  const pageNumber = Math.max(1, Number(route.query.page) || 1)
  if (!q) {
    data.value = null
    error.value = ''
    loading.value = false
    page.value = 1
    return
  }
  fetchResults(q, pageNumber, scope.value)
}

function submitSearch() {
  const q = input.value.trim()
  if (!q) {
    inputEl.value?.focus()
    return
  }
  router.push({ name: 'search', query: buildQuery(q, 1) })
}

function searchExample(value) {
  input.value = value
  router.push({ name: 'search', query: buildQuery(value, 1) })
}

function onScopeChange() {
  const q = input.value.trim() || String(route.query.q || '')
  if (!q) return
  router.push({ name: 'search', query: buildQuery(q, 1) })
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.value) return
  router.push({ name: 'search', query: buildQuery(String(route.query.q || input.value), target) })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function openResult(item) {
  router.push({
    name: 'read',
    params: { bookId: item.bookId, chapter: item.chapter },
    query: { verse: String(item.verse) },
  })
}

function openReference() {
  const reference = data.value?.reference
  if (!reference) return
  const range = reference.verseEnd > reference.verseStart
    ? `${reference.verseStart}-${reference.verseEnd}`
    : String(reference.verseStart)
  router.push({
    name: 'read',
    params: { bookId: reference.bookId, chapter: reference.chapter },
    query: { verse: range },
  })
}

async function copyResult(item) {
  const ok = await copyText(
    buildCopyText(item.bookName, item.chapter, [{ verse: item.verse, text: item.text }], store.settings),
  )
  if (!ok) {
    toast('复制失败，请检查剪贴板权限', 'error')
    return
  }
  toast(`已复制 ${item.ref}`)
  logEvent('copy', {
    message: `复制 ${item.ref}`,
    bookId: item.bookId,
    chapter: item.chapter,
    verse: item.verse,
    detail: 'search result',
  })
}

function clearQuery() {
  input.value = ''
  router.push({ name: 'search', query: {} })
  nextTick(() => inputEl.value?.focus())
}

onMounted(async () => {
  await ensureBooks().catch(() => {})
  if (route.query.focus === '1' || !route.query.q) {
    nextTick(() => inputEl.value?.focus())
  }
})

watch(() => route.query, applyRoute, { immediate: true, deep: true })
</script>

<template>
  <div class="search-page">
    <div class="search-container">
      <header class="search-head">
        <p class="section-eyebrow"><Sparkles :size="14" /> SCRIPTURE SEARCH</p>
        <h1>在经文中寻找</h1>
        <p class="search-sub">输入关键词，或直接输入书卷章节，例如「约3:16」「诗23篇」。</p>
      </header>

      <form class="search-panel" role="search" @submit.prevent="submitSearch">
        <div class="search-input-row">
          <Search :size="21" :stroke-width="1.7" />
          <input
            ref="inputEl"
            v-model="input"
            type="search"
            placeholder="搜索经文内容，或输入 约3:16 / John 3:16"
            aria-label="搜索经文"
            autocomplete="off"
            spellcheck="false"
          />
          <button v-if="input" type="button" class="search-clear" aria-label="清空" @click="clearQuery">
            <X :size="16" />
          </button>
          <button type="submit" class="btn primary search-submit" :disabled="loading">
            <Loader2 v-if="loading" class="spin" :size="17" />
            <template v-else>搜索</template>
          </button>
        </div>
        <div class="search-filters">
          <label class="scope-select">
            <BookOpen :size="14" />
            <select v-model="scope" aria-label="搜索范围" @change="onScopeChange">
              <option value="">全本圣经</option>
              <optgroup label="旧约">
                <option v-for="book in store.books.filter((b) => b.testament === 'OT')" :key="book.id" :value="String(book.id)">
                  {{ book.name }}
                </option>
              </optgroup>
              <optgroup label="新约">
                <option v-for="book in store.books.filter((b) => b.testament === 'NT')" :key="book.id" :value="String(book.id)">
                  {{ book.name }}
                </option>
              </optgroup>
            </select>
          </label>
          <span class="search-scope-note">在「{{ scopeLabel }}」中搜索</span>
          <span class="search-tip">多个关键词用空格分隔，例如「神 爱」</span>
        </div>
      </form>

      <div v-if="!route.query.q" class="search-landing">
        <div class="search-examples">
          <button v-for="example in examples" :key="example.label" type="button" class="example-card" @click="searchExample(example.label)">
            <span class="example-label">{{ example.label }}</span>
            <span class="example-hint">{{ example.hint }}</span>
            <ArrowRight :size="16" />
          </button>
        </div>

        <div v-if="store.searchHistory.length" class="search-history">
          <div class="search-history-head">
            <h3>最近搜索</h3>
            <button type="button" class="text-btn" @click="clearSearchHistory">清空</button>
          </div>
          <div class="history-chips">
            <button v-for="item in store.searchHistory" :key="item" type="button" @click="searchExample(item)">
              {{ item }}
            </button>
          </div>
        </div>

        <div class="search-guide">
          <h3>搜索方式</h3>
          <ul>
            <li><b>关键词</b>：如「神爱世人」「复活」「盼望」，支持空格组合。</li>
            <li><b>经文引用</b>：如「约3:16」「约翰福音3章16节」「诗23篇」。支持中文简称与英文缩写。</li>
            <li><b>范围限定</b>：先在左侧选择书卷，再输入关键词，只在该卷中查找。</li>
          </ul>
        </div>
      </div>

      <template v-else>
        <div v-if="loading" class="search-loading">
          <div v-for="n in 5" :key="n" class="result-skeleton">
            <span class="sk-line sk-ref" />
            <span class="sk-line" :style="{ width: `${64 + ((n * 9) % 28)}%` }" />
            <span class="sk-line" :style="{ width: `${40 + ((n * 7) % 30)}%` }" />
          </div>
        </div>

        <div v-else-if="error" class="search-error">
          <p>{{ error }}</p>
          <button type="button" class="btn ghost" @click="applyRoute">重试</button>
        </div>

        <template v-else-if="data">
          <div v-if="data.reference" class="reference-banner">
            <div class="reference-info">
              <span>识别为经文引用</span>
              <strong>{{ data.reference.display }}</strong>
              <small>{{ data.reference.bookName }} 第 {{ data.reference.chapter }} 章</small>
            </div>
            <button type="button" class="btn primary" @click="openReference">
              打开经文
              <ArrowRight :size="16" />
            </button>
          </div>

          <div class="search-result-head">
            <p>
              <template v-if="data.total">
                找到 <strong>{{ data.total.toLocaleString() }}</strong> 处经文
              </template>
              <template v-else>没有找到匹配的经文</template>
              <span class="search-took">用时 {{ data.took }} ms</span>
            </p>
            <p v-if="data.terms?.length" class="search-terms">
              <span v-for="term in data.terms" :key="term">{{ term }}</span>
            </p>
          </div>

          <div v-if="data.items?.length" class="result-list">
            <article v-for="item in data.items" :key="`${item.bookId}-${item.chapter}-${item.verse}`" class="result-card">
              <header class="result-head">
                <RouterLink
                  class="result-ref"
                  :to="{ name: 'read', params: { bookId: item.bookId, chapter: item.chapter }, query: { verse: item.verse } }"
                >
                  {{ item.ref }}
                </RouterLink>
                <span class="result-book">{{ item.bookName }}</span>
                <button type="button" class="icon-btn result-copy" aria-label="复制这节经文" @click="copyResult(item)">
                  <Copy :size="15" />
                </button>
              </header>
              <p class="result-text">
                <template v-for="(part, index) in splitHighlights(item.text, item.highlights)" :key="index">
                  <mark v-if="part.mark">{{ part.text }}</mark>
                  <template v-else>{{ part.text }}</template>
                </template>
              </p>
              <footer class="result-foot">
                <button type="button" class="text-btn" @click="openResult(item)">
                  阅读上下文
                  <ArrowRight :size="14" />
                </button>
              </footer>
            </article>
          </div>

          <div v-else class="search-empty">
            <p class="empty-mark">“</p>
            <h3>没有找到「{{ route.query.q }}」</h3>
            <p>试试更短的关键词，或检查经文引用格式，例如「约3:16」。</p>
            <div class="history-chips">
              <button v-for="example in examples" :key="example.label" type="button" @click="searchExample(example.label)">
                {{ example.label }}
              </button>
            </div>
          </div>

          <nav v-if="totalPages > 1 && !data.reference" class="pagination" aria-label="搜索分页">
            <button type="button" class="page-btn" :disabled="page <= 1" @click="goPage(page - 1)">
              <ChevronLeft :size="16" />
            </button>
            <template v-for="(item, index) in pageNumbers" :key="`${item}-${index}`">
              <span v-if="item === '…'" class="page-gap">…</span>
              <button
                v-else
                type="button"
                class="page-btn"
                :class="{ active: item === page }"
                :aria-current="item === page ? 'page' : undefined"
                @click="goPage(item)"
              >
                {{ item }}
              </button>
            </template>
            <button type="button" class="page-btn" :disabled="page >= totalPages" @click="goPage(page + 1)">
              <ChevronRight :size="16" />
            </button>
          </nav>
        </template>
      </template>
    </div>
  </div>
</template>
