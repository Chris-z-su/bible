<script setup>
import { computed } from 'vue'
import { Check, Copy, Link2, X } from 'lucide-vue-next'
import {
  clearSelection,
  copySelectionText,
  selection,
  selectionLabel,
  selectionLink,
} from '../composables/useSelection'
import { toast } from '../store'
import { logEvent } from '../api/client'
import { copyText } from '../utils/format'

const label = computed(() => selectionLabel())
const count = computed(() => selection.verses.length)

async function handleCopy() {
  const ok = await copySelectionText()
  if (!ok) {
    toast('复制失败，请检查浏览器剪贴板权限', 'error')
    return
  }
  toast(`已复制 ${count.value} 节经文`)
  logEvent('copy', {
    message: `复制 ${label.value}`,
    bookId: selection.bookId,
    chapter: selection.chapter,
    verse: selection.verses[0]?.verse,
    detail: `${count.value} verses`,
  })
}

async function handleLink() {
  const ok = await copyText(selectionLink())
  if (!ok) {
    toast('链接复制失败', 'error')
    return
  }
  toast('经文链接已复制')
  logEvent('copy', {
    message: `复制链接 ${label.value}`,
    bookId: selection.bookId,
    chapter: selection.chapter,
    verse: selection.verses[0]?.verse,
    detail: 'link',
  })
}
</script>

<template>
  <Teleport to="body">
    <Transition name="slide-up">
      <div v-if="selection.active" class="selection-bar" role="toolbar" aria-label="已选经文操作">
        <div class="selection-info">
          <span class="selection-count">{{ count }}</span>
          <div class="selection-text">
            <strong>{{ label }}</strong>
            <small>已选 {{ count }} 节 · 点击经文可继续加选</small>
          </div>
        </div>
        <div class="selection-actions">
          <button type="button" class="selection-btn ghost" @click="handleLink">
            <Link2 :size="16" />
            <span class="selection-btn-text">链接</span>
          </button>
          <button type="button" class="selection-btn primary" @click="handleCopy">
            <Copy :size="16" />
            复制经文
          </button>
          <button type="button" class="selection-close" aria-label="取消选择" @click="clearSelection">
            <X :size="17" />
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
