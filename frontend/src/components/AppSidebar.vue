<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronDown, Search, X } from 'lucide-vue-next'
import { store } from '../store'

const props = defineProps({
  bookId: { type: [Number, String], default: null },
  chapter: { type: [Number, String], default: null },
  open: { type: Boolean, default: false },
})
const emit = defineEmits(['close'])
const router = useRouter()

const filter = ref('')
const activeTestament = ref('OT')
const expandedBookId = ref(null)

const currentBook = computed(() => store.books.find((b) => Number(b.id) === Number(props.bookId)) || null)

watch(
  () => [props.bookId, store.books.length],
  () => {
    const book = store.books.find((b) => Number(b.id) === Number(props.bookId))
    if (book) {
      activeTestament.value = book.testament
      expandedBookId.value = book.id
      nextTick(scrollToCurrentChapter)
    }
  },
  { immediate: true },
)

watch(
  () => props.open,
  (open) => {
    if (open) {
      expandedBookId.value = Number(props.bookId) || expandedBookId.value
      nextTick(scrollToCurrentChapter)
    }
  },
)

function scrollToCurrentChapter() {
  if (!props.bookId || !props.chapter) return
  const el = document.getElementById(`chapter-${props.bookId}-${props.chapter}`)
  if (el) el.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
}

const filteredBooks = computed(() => {
  const keyword = filter.value.trim().toLowerCase().replace(/\s+/g, '')
  let books = store.books
  if (keyword) {
    books = books.filter((book) => {
      const haystack = `${book.name}${book.shortName}${book.englishName}${book.englishAbbr}`.toLowerCase().replace(/\s+/g, '')
      return haystack.includes(keyword)
    })
  } else {
    books = books.filter((book) => book.testament === activeTestament.value)
  }
  return books
})

const groups = computed(() => {
  const map = new Map()
  for (const book of filteredBooks.value) {
    if (!map.has(book.category)) map.set(book.category, [])
    map.get(book.category).push(book)
  }
  return [...map.entries()].map(([category, books]) => ({ category, books }))
})

const otCount = computed(() => store.books.filter((b) => b.testament === 'OT').length)
const ntCount = computed(() => store.books.filter((b) => b.testament === 'NT').length)
const filteredCount = computed(() => filteredBooks.value.length)

function toggleBook(book) {
  expandedBookId.value = expandedBookId.value === book.id ? null : book.id
}

function openChapter(book, chapter) {
  emit('close')
  router.push({ name: 'read', params: { bookId: book.id, chapter } })
}

function setTestament(value) {
  activeTestament.value = value
  filter.value = ''
}

onMounted(() => {
  if (currentBook.value) expandedBookId.value = currentBook.value.id
  nextTick(scrollToCurrentChapter)
})
</script>

<template>
  <Transition name="fade">
    <div v-if="open" class="sidebar-backdrop" @click="emit('close')" />
  </Transition>

  <aside class="app-sidebar" :class="{ 'is-open': open }" aria-label="书卷与章节选择">
    <div class="sidebar-head">
      <div>
        <p class="sidebar-eyebrow">旧约与新约</p>
        <h2>选择书卷</h2>
      </div>
      <button class="icon-btn sidebar-close" type="button" aria-label="关闭书卷选择" @click="emit('close')">
        <X :size="19" />
      </button>
    </div>

    <div class="book-filter">
      <Search :size="15" />
      <input v-model="filter" type="search" placeholder="筛选书卷，如 约翰 / John" aria-label="筛选书卷" />
      <button v-if="filter" type="button" class="filter-clear" aria-label="清空筛选" @click="filter = ''">
        <X :size="13" />
      </button>
    </div>

    <div v-if="!filter" class="testament-tabs">
      <button type="button" :class="{ active: activeTestament === 'OT' }" @click="setTestament('OT')">
        旧约 <span>{{ otCount }}</span>
      </button>
      <button type="button" :class="{ active: activeTestament === 'NT' }" @click="setTestament('NT')">
        新约 <span>{{ ntCount }}</span>
      </button>
    </div>
    <p v-else class="filter-result">找到 {{ filteredCount }} 卷书</p>

    <div class="book-scroll">
      <section v-for="group in groups" :key="group.category" class="book-group">
        <h3 class="book-group-title">{{ group.category }}</h3>
        <div v-for="book in group.books" :key="book.id" class="book-item">
          <button
            type="button"
            class="book-row"
            :class="{
              active: Number(bookId) === book.id,
              expanded: expandedBookId === book.id,
            }"
            :aria-expanded="expandedBookId === book.id"
            @click="toggleBook(book)"
          >
            <span class="book-name">
              {{ book.name }}
              <small v-if="filter">{{ book.testament === 'OT' ? '旧约' : '新约' }}</small>
            </span>
            <span class="book-count">{{ book.chapterCount }} 章</span>
            <ChevronDown class="book-chevron" :size="15" :class="{ rotated: expandedBookId === book.id }" />
          </button>

          <Transition name="chapter-expand">
            <div v-if="expandedBookId === book.id" class="chapter-panel">
              <div class="chapter-grid">
                <button
                  v-for="n in book.chapterCount"
                  :id="`chapter-${book.id}-${n}`"
                  :key="n"
                  type="button"
                  class="chapter-btn"
                  :class="{ current: Number(bookId) === book.id && Number(chapter) === n }"
                  :aria-current="Number(bookId) === book.id && Number(chapter) === n ? 'page' : undefined"
                  @click="openChapter(book, n)"
                >
                  {{ n }}
                </button>
              </div>
              <p class="chapter-hint">共 {{ book.chapterCount }} 章 · {{ book.verseCount.toLocaleString() }} 节</p>
            </div>
          </Transition>
        </div>
      </section>

      <div v-if="!groups.length" class="sidebar-empty">
        <p>没有找到“{{ filter }}”</p>
        <button type="button" class="text-btn" @click="filter = ''">清空筛选</button>
      </div>
    </div>
  </aside>
</template>
