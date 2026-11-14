<script setup>
import { computed } from 'vue'
import { Copy } from 'lucide-vue-next'
import { selection } from '../composables/useSelection'
import { store } from '../store'
import { splitParentheses } from '../utils/format'

const props = defineProps({
  book: { type: Object, required: true },
  chapter: { type: Number, required: true },
  verses: { type: Array, default: () => [] },
  titles: { type: Array, default: () => [] },
})
const emit = defineEmits(['select', 'copy'])

const settings = computed(() => store.settings)

const titleMap = computed(() => {
  const map = new Map()
  for (const title of props.titles) {
    if (!map.has(title.verse)) map.set(title.verse, [])
    map.get(title.verse).push(title)
  }
  return map
})

const blocks = computed(() => {
  const list = []
  for (const verse of props.verses) {
    const titles = titleMap.value.get(verse.verse)
    if (titles?.length) list.push({ type: 'titles', verse: verse.verse, titles })
    list.push({ type: 'verse', verse })
  }
  return list
})

const sections = computed(() => {
  const result = []
  let current = { titles: [], verses: [] }
  for (const verse of props.verses) {
    const titles = titleMap.value.get(verse.verse) || []
    if (titles.length) {
      if (current.verses.length) {
        result.push(current)
        current = { titles: [...titles], verses: [] }
      } else {
        current.titles.push(...titles)
      }
    }
    current.verses.push(verse)
  }
  if (current.verses.length || current.titles.length) result.push(current)
  return result
})

function isSelected(verse) {
  return (
    selection.active &&
    selection.bookId === props.book.id &&
    selection.chapter === props.chapter &&
    selection.verses.some((item) => item.verse === verse.verse)
  )
}

function onSelect(event, verse) {
  emit('select', event, verse)
}

function onKey(event, verse) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    emit('select', event, verse)
  }
}
</script>

<template>
  <div v-if="verses.length" class="verse-list" :class="{ 'is-paragraph': settings.paragraph }">
    <template v-if="!settings.paragraph">
      <template v-for="item in blocks" :key="item.type === 'titles' ? `t-${item.verse}` : `v-${item.verse.verse}`">
        <div v-if="item.type === 'titles'" class="section-titles">
          <h3
            v-for="(title, titleIndex) in item.titles"
            :key="`${item.verse}-${titleIndex}`"
            class="section-title"
            :class="title.kind"
          >
            {{ title.text }}
          </h3>
        </div>
        <div
          v-else
          :id="`verse-${item.verse.verse}`"
          class="verse"
          :class="{ selected: isSelected(item.verse), 'no-numbers': !settings.showVerseNumbers }"
          role="button"
          tabindex="0"
          :aria-pressed="isSelected(item.verse)"
          :aria-label="`第 ${item.verse.verse} 节，点击选择`"
          @click="onSelect($event, item.verse)"
          @keydown="onKey($event, item.verse)"
        >
          <span v-if="settings.showVerseNumbers" class="verse-num">{{ item.verse.verse }}</span>
          <span class="verse-text"><template v-for="(part, partIndex) in splitParentheses(item.verse.text)" :key="partIndex"><span v-if="part.paren" class="verse-paren">{{ part.text }}</span><template v-else>{{ part.text }}</template></template></span>
          <button
            type="button"
            class="verse-copy"
            aria-label="复制这一节"
            title="复制这一节"
            @click.stop="emit('copy', item.verse)"
          >
            <Copy :size="14" />
          </button>
        </div>
      </template>
    </template>

    <template v-else>
      <section v-for="(section, index) in sections" :key="index" class="flow-section">
        <div v-if="section.titles.length" class="section-titles">
          <h3 v-for="(title, titleIndex) in section.titles" :key="titleIndex" class="section-title" :class="title.kind">
            {{ title.text }}
          </h3>
        </div>
        <p class="flow-paragraph">
          <span
            v-for="verse in section.verses"
            :id="`verse-${verse.verse}`"
            :key="verse.verse"
            class="verse flow-verse"
            :class="{ selected: isSelected(verse) }"
            role="button"
            tabindex="0"
            :aria-pressed="isSelected(verse)"
            @click="onSelect($event, verse)"
            @keydown="onKey($event, verse)"
          >
            <sup v-if="settings.showVerseNumbers" class="verse-num-inline">{{ verse.verse }}</sup>
            <span class="verse-text"><template v-for="(part, partIndex) in splitParentheses(verse.text)" :key="partIndex"><span v-if="part.paren" class="verse-paren">{{ part.text }}</span><template v-else>{{ part.text }}</template></template></span>
          </span>
        </p>
      </section>
    </template>

    <div class="chapter-end" aria-hidden="true">
      <span />
      <b>✦</b>
      <span />
    </div>
  </div>

  <div v-else class="reader-empty">
    <p>本章暂无经文数据。</p>
  </div>
</template>
