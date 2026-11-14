<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOpen, Search, Sun, Moon, Settings2, Loader2 } from 'lucide-vue-next'
import { api, logEvent } from '../api/client'
import { store, toggleTheme, toast } from '../store'

const route = useRoute()
const router = useRouter()
const q = ref('')
const loading = ref(false)

const readTarget = computed(() => {
  if (store.lastRead?.bookId) {
    return `/read/${store.lastRead.bookId}/${store.lastRead.chapter}`
  }
  return '/read/1/1'
})

const isNight = computed(() => store.settings.theme === 'night')

function goReference(reference, rawQuery) {
  const range = reference.verseEnd && reference.verseEnd > reference.verseStart
    ? `${reference.verseStart}-${reference.verseEnd}`
    : String(reference.verseStart || '')
  const query = range ? { verse: range } : {}
  router.push({
    name: 'read',
    params: { bookId: reference.bookId, chapter: reference.chapter },
    query,
  })
  logEvent('jump', {
    message: `跳转 ${reference.display || rawQuery}`,
    bookId: reference.bookId,
    chapter: reference.chapter,
    verse: reference.verseStart,
    query: rawQuery,
  })
}

async function submitSearch() {
  const raw = q.value.trim()
  if (!raw || loading.value) return
  loading.value = true
  try {
    const reference = await api.parseReference(raw)
    goReference(reference, raw)
    q.value = ''
  } catch (error) {
    if (error.status === 400) {
      router.push({ name: 'search', query: { q: raw } })
      q.value = ''
    } else {
      toast(error.message || '解析引用失败', 'error')
      router.push({ name: 'search', query: { q: raw } })
    }
  } finally {
    loading.value = false
  }
}

function openSearch() {
  router.push({ name: 'search', query: { focus: '1' } })
}
</script>

<template>
  <header class="app-header">
    <div class="header-inner">
      <RouterLink class="brand" to="/" aria-label="精读圣经首页">
        <span class="brand-mark"><BookOpen :size="21" :stroke-width="1.55" /></span>
        <span class="brand-text">
          <strong>精读圣经</strong>
          <small>BIBLE READER</small>
        </span>
      </RouterLink>

      <nav class="main-nav" aria-label="主导航">
        <RouterLink :to="readTarget" class="nav-link" :class="{ active: route.name === 'read' }">阅读</RouterLink>
        <RouterLink to="/search" class="nav-link" :class="{ active: route.name === 'search' }">搜索</RouterLink>
        <RouterLink to="/logs" class="nav-link" :class="{ active: route.name === 'logs' }">日志</RouterLink>
      </nav>

      <form class="header-search" role="search" @submit.prevent="submitSearch">
        <Search class="search-icon" :size="17" :stroke-width="1.7" />
        <input
          v-model="q"
          type="search"
          placeholder="搜索经文，或输入 约3:16 跳转"
          aria-label="搜索经文或输入经文引用跳转"
          autocomplete="off"
          spellcheck="false"
        />
        <kbd v-if="!loading" class="search-kbd">/</kbd>
        <Loader2 v-else class="spin search-loading" :size="16" />
      </form>

      <div class="header-actions">
        <button class="icon-btn mobile-only" type="button" aria-label="搜索" @click="openSearch">
          <Search :size="19" :stroke-width="1.7" />
        </button>
        <button
          class="icon-btn"
          type="button"
          :aria-label="isNight ? '切换到浅色主题' : '切换到夜间主题'"
          :title="isNight ? '浅色主题' : '夜间主题'"
          @click="toggleTheme"
        >
          <Sun v-if="isNight" :size="19" :stroke-width="1.7" />
          <Moon v-else :size="19" :stroke-width="1.7" />
        </button>
        <button
          class="icon-btn"
          type="button"
          aria-label="阅读设置"
          title="阅读设置"
          @click="store.settingsOpen = !store.settingsOpen"
        >
          <Settings2 :size="19" :stroke-width="1.7" />
        </button>
      </div>
    </div>
  </header>
</template>
