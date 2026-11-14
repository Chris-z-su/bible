<script setup>
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import SelectionBar from './components/SelectionBar.vue'
import SettingsPanel from './components/SettingsPanel.vue'
import ToastHost from './components/ToastHost.vue'
import { ensureBooks, ensureStats, store } from './store'
import { clearSelection } from './composables/useSelection'

const route = useRoute()
const router = useRouter()

const readerVars = computed(() => ({
  '--reader-font-size': `${store.settings.fontSize}px`,
  '--reader-line-height': String(store.settings.lineHeight),
}))

function isEditingTarget(target) {
  if (!target) return false
  const tag = String(target.tagName || '').toLowerCase()
  return tag === 'input' || tag === 'textarea' || tag === 'select' || target.isContentEditable
}

function onKeydown(event) {
  if (event.key === 'Escape') {
    clearSelection()
    store.settingsOpen = false
    store.menuOpen = false
    return
  }
  if (!isEditingTarget(event.target) && event.key === '/') {
    event.preventDefault()
    router.push({ name: 'search', query: { focus: '1' } })
  }
}

watch(
  () => route.fullPath,
  () => {
    if (!route.query.verse) clearSelection()
  },
)

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  ensureBooks().catch(() => {})
  ensureStats()
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="app-shell" :style="readerVars" :class="{ 'reduce-motion': store.settings.reduceMotion }">
    <AppHeader />
    <main class="app-main">
      <RouterView v-slot="{ Component }">
        <Transition name="page-fade" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>

    <footer class="app-footer">
      <div class="footer-inner">
        <p class="footer-verse">你的话是我脚前的灯，是我路上的光。<span>诗篇 119:105</span></p>
        <p class="footer-meta">
          和合本经文 · 精读圣经离线数据库
          <span class="footer-dot">·</span>
          Spring Boot / MyBatis / MySQL / Redis / Elasticsearch
        </p>
      </div>
    </footer>

    <SelectionBar />
    <SettingsPanel />
    <ToastHost />
  </div>
</template>
