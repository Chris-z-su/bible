<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AlignJustify,
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Copy,
  Loader2,
  Menu,
  MousePointerClick,
  RefreshCw,
  Settings2,
} from 'lucide-vue-next'
import AppSidebar from '../components/AppSidebar.vue'
import VerseList from '../components/VerseList.vue'
import { api, logEvent } from '../api/client'
import { bookById, ensureBooks, recordRead, saveSettings, store, toast } from '../store'
import { clearSelection, handleVerseClick, selection } from '../composables/useSelection'
import { buildCopyText, copyText } from '../utils/format'

const route = useRoute()
const router = useRouter()

const data = ref(null)
const loading = ref(true)
const error = ref('')
const sidebarOpen = ref(false)
const copying = ref(false)
let requestToken = 0

const book = computed(() => data.value?.book || bookById(route.params.bookId) || null)
const chapter = computed(() => Number(data.value?.chapter || route.params.chapter || 1))
const verses = computed(() => data.value?.verses || [])
const titles = computed(() => data.value?.titles || [])
const prev = computed(() => data.value?.prev || null)
const next = computed(() => data.value?.next || null)
const paddedChapter = computed(() => String(chapter.value).padStart(2, '0'))
const progressPercent = computed(() => {
  if (!book.value?.chapterCount) return 0
  return Math.min(100, Math.round((chapter.value / book.value.chapterCount) * 100))
})

function isEditingTarget(target) {
  if (!target) return false
  const tag = String(target.tagName || '').toLowerCase()
  return tag === 'input' || tag === 'textarea' || tag === 'select' || target.isContentEditable
}

async function loadChapter() {
  const bookId = Number(route.params.bookId)
  const chapterNumber = Number(route.params.chapter)
  if (!bookId || !chapterNumber || chapterNumber < 1) {
    error.value = '章节参数不正确'
    loading.value = false
    return
  }
  const token = ++requestToken
  loading.value = true
  error.value = ''
  clearSelection()
  try {
    await ensureBooks()
    const result = await api.getChapter(bookId, chapterNumber)
    if (token !== requestToken) return
    data.value = result
    recordRead(result.book, result.chapter)
    document.title = `${result.book.name} 第 ${result.chapter} 章 · 精读圣经`
    logEvent('read', {
      message: `阅读 ${result.book.name} 第 ${result.chapter} 章`,
      bookId: result.book.id,
      chapter: result.chapter,
      detail: `${result.verses?.length || 0} 节`,
    })
    loading.value = false
    await nextTick()
    scrollToVerse()
    window.scrollTo({ top: 0, behavior: 'auto' })
  } catch (err) {
    if (token !== requestToken) return
    error.value = err.message || '章节加载失败'
    data.value = null
    loading.value = false
    logEvent('error', {
      level: 'ERROR',
      message: `加载章节失败：${error.value}`,
      bookId,
      chapter: chapterNumber,
    })
  }
}

function scrollToVerse() {
  const raw = String(route.query.verse || '')
  if (!raw) return
  const start = Number(raw.split('-')[0])
  if (!start) return
  const el = document.getElementById(`verse-${start}`)
  if (!el) return
  window.setTimeout(() => {
    el.scrollIntoView({ block: 'center', behavior: 'smooth' })
    el.classList.add('verse-flash')
    window.setTimeout(() => el.classList.remove('verse-flash'), 2400)
  }, 140)
}

function goTo(target) {
  if (!target) return
  router.push({
    name: 'read',
    params: { bookId: target.bookId, chapter: target.chapter },
  })
}

function goPrev() {
  if (prev.value) goTo(prev.value)
}

function goNext() {
  if (next.value) goTo(next.value)
}

function onSelect(event, verse) {
  if (!book.value) return
  handleVerseClick(event, book.value, chapter.value, verse, verses.value)
}

async function copyVerse(verse) {
  const ok = await copyText(buildCopyText(book.value.name, chapter.value, [verse], store.settings))
  if (!ok) {
    toast('复制失败，请检查剪贴板权限', 'error')
    return
  }
  toast(`已复制 ${book.value.name} ${chapter.value}:${verse.verse}`)
  logEvent('copy', {
    message: `复制 ${book.value.name} ${chapter.value}:${verse.verse}`,
    bookId: book.value.id,
    chapter: chapter.value,
    verse: verse.verse,
  })
}

async function copyChapter() {
  if (!verses.value.length || copying.value) return
  copying.value = true
  const ok = await copyText(
    buildCopyText(book.value.name, chapter.value, verses.value, { ...store.settings, wholeChapter: true }),
  )
  copying.value = false
  if (!ok) {
    toast('复制失败，请检查剪贴板权限', 'error')
    return
  }
  toast(`已复制 ${book.value.name} 第 ${chapter.value} 章`)
  logEvent('copy', {
    message: `复制整章 ${book.value.name} ${chapter.value}`,
    bookId: book.value.id,
    chapter: chapter.value,
    detail: `${verses.value.length} verses`,
  })
}

function toggleParagraph() {
  saveSettings({ paragraph: !store.settings.paragraph })
}

function stepFont(delta) {
  saveSettings({ fontSize: Math.min(26, Math.max(16, store.settings.fontSize + delta)) })
}

function onKeydown(event) {
  if (isEditingTarget(event.target) || store.settingsOpen) return
  if (event.key === 'ArrowLeft') goPrev()
  if (event.key === 'ArrowRight') goNext()
}

watch(() => [route.params.bookId, route.params.chapter], loadChapter, { immediate: true })
watch(() => route.query.verse, () => nextTick(scrollToVerse))

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="read-layout">
    <AppSidebar
      :book-id="route.params.bookId"
      :chapter="route.params.chapter"
      :open="sidebarOpen"
      @close="sidebarOpen = false"
    />

    <article class="reader">
      <div class="reader-topline">
        <button class="catalog-btn" type="button" @click="sidebarOpen = true">
          <Menu :size="17" />
          书卷章节
        </button>
        <template v-if="book">
          <nav class="breadcrumb" aria-label="当前位置">
            <RouterLink to="/">{{ book.testament === 'OT' ? '旧约' : '新约' }}</RouterLink>
            <span>/</span>
            <span>{{ book.category }}</span>
            <span>/</span>
            <strong>{{ book.name }}</strong>
          </nav>
        </template>
        <span class="reader-topline-spacer" />
        <span class="version-tag">和合本</span>
      </div>

      <template v-if="loading">
        <div class="reader-skeleton" aria-busy="true" aria-label="经文加载中">
          <div class="sk-line sk-kicker" />
          <div class="sk-line sk-title" />
          <div class="sk-line sk-meta" />
          <div class="sk-verse" v-for="n in 9" :key="n">
            <span class="sk-num" />
            <span class="sk-text" :style="{ width: `${58 + ((n * 13) % 36)}%` }" />
          </div>
        </div>
      </template>

      <div v-else-if="error" class="reader-error">
        <p class="error-icon">!</p>
        <h2>无法打开这一章</h2>
        <p>{{ error }}</p>
        <div class="error-actions">
          <button type="button" class="btn primary" @click="loadChapter">
            <RefreshCw :size="16" /> 重新加载
          </button>
          <RouterLink to="/" class="btn ghost"><ArrowLeft :size="16" /> 返回首页</RouterLink>
        </div>
      </div>

      <template v-else-if="data">
        <header class="chapter-header">
          <div class="chapter-head-main">
            <p class="chapter-kicker">{{ book.testament === 'OT' ? '旧约' : '新约' }} · {{ book.category }}</p>
            <h1>
              <span class="chapter-book-name">{{ book.name }}</span>
              <span class="chapter-label">第 {{ chapter }} 章</span>
            </h1>
            <p class="chapter-meta">
              <span>{{ book.englishName }}</span>
              <i />
              <span>{{ data.verseCount }} 节</span>
              <i />
              <span>全卷 {{ book.chapterCount }} 章</span>
            </p>
          </div>
          <div class="chapter-figure" aria-hidden="true">
            <span>{{ paddedChapter }}</span>
            <svg viewBox="0 0 120 20" preserveAspectRatio="none">
              <path d="M2 18 C 30 2, 90 2, 118 18" fill="none" stroke="currentColor" stroke-width="1.4" />
            </svg>
          </div>
        </header>

        <div class="reader-toolbar">
          <div class="toolbar-group chapter-switcher">
            <button type="button" class="tool-btn" :disabled="!prev" aria-label="上一章" @click="goPrev">
              <ChevronLeft :size="17" />
              <span>上一章</span>
            </button>
            <div class="chapter-progress" :title="`本章进度 ${progressPercent}%`">
              <span>{{ chapter }} / {{ book.chapterCount }}</span>
              <i><b :style="{ width: `${progressPercent}%` }" /></i>
            </div>
            <button type="button" class="tool-btn" :disabled="!next" aria-label="下一章" @click="goNext">
              <span>下一章</span>
              <ChevronRight :size="17" />
            </button>
          </div>

          <div class="toolbar-group toolbar-tools">
            <button type="button" class="tool-btn compact" title="减小字号" aria-label="减小字号" @click="stepFont(-1)">
              <span class="font-step">A−</span>
            </button>
            <button type="button" class="tool-btn compact" title="增大字号" aria-label="增大字号" @click="stepFont(1)">
              <span class="font-step plus">A+</span>
            </button>
            <button
              type="button"
              class="tool-btn compact"
              :class="{ toggled: store.settings.paragraph }"
              title="段落连读"
              :aria-pressed="store.settings.paragraph"
              @click="toggleParagraph"
            >
              <AlignJustify :size="16" />
              <span class="tool-label">连读</span>
            </button>
            <button type="button" class="tool-btn compact" title="复制本章" @click="copyChapter">
              <Loader2 v-if="copying" class="spin" :size="16" />
              <Copy v-else :size="16" />
              <span class="tool-label">复制本章</span>
            </button>
            <button type="button" class="tool-btn compact" title="阅读设置" @click="store.settingsOpen = true">
              <Settings2 :size="16" />
            </button>
          </div>
        </div>

        <p class="reader-select-hint">
          <MousePointerClick :size="13" />
          点击经文选择多节复制 · Shift 连选 · Ctrl/Cmd 多选
        </p>

        <VerseList
          :book="book"
          :chapter="chapter"
          :verses="verses"
          :titles="titles"
          @select="onSelect"
          @copy="copyVerse"
        />

        <p v-if="selection.active" class="selection-hint">
          已选择 {{ selection.verses.length }} 节 · 使用底部工具栏复制，或按 Esc 取消
        </p>

        <nav class="chapter-foot" aria-label="章节翻页">
          <button v-if="prev" type="button" class="chapter-foot-card prev" @click="goPrev">
            <span class="foot-dir"><ChevronLeft :size="15" /> 上一章</span>
            <strong>{{ prev.bookName }} 第 {{ prev.chapter }} 章</strong>
          </button>
          <span v-else class="chapter-foot-placeholder" />
          <button v-if="next" type="button" class="chapter-foot-card next" @click="goNext">
            <span class="foot-dir">下一章 <ChevronRight :size="15" /></span>
            <strong>{{ next.bookName }} 第 {{ next.chapter }} 章</strong>
          </button>
          <span v-else class="chapter-foot-placeholder" />
        </nav>
      </template>
    </article>
  </div>
</template>
