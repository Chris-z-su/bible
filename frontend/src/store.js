import { reactive, computed } from 'vue'
import { api, logEvent } from './api/client'

const SETTINGS_KEY = 'bible:settings:v1'
const LAST_READ_KEY = 'bible:last-read:v1'
const HISTORY_KEY = 'bible:history:v1'
const SEARCH_HISTORY_KEY = 'bible:search-history:v1'

export const DEFAULT_SETTINGS = {
  theme: 'paper',
  fontSize: 19,
  lineHeight: 2,
  paragraph: false,
  showVerseNumbers: true,
  reduceMotion: false,
  copyPreset: 'standard',
  copyNumbers: true,
  copyRef: true,
  copyLayout: 'line',
  copyTemplate: '',
}

function readJSON(key, fallback) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : fallback
  } catch {
    return fallback
  }
}

function writeJSON(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    /* 隐私模式忽略 */
  }
}

function normalizeSettings(value) {
  return { ...DEFAULT_SETTINGS, ...(value || {}) }
}

export function applyTheme(theme) {
  document.documentElement.dataset.theme = theme
  const colors = { paper: '#f5f0e6', sepia: '#e9dfc6', night: '#12110e' }
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', colors[theme] || colors.paper)
}

const initialSettings = normalizeSettings(readJSON(SETTINGS_KEY, null))
applyTheme(initialSettings.theme)

export const store = reactive({
  books: [],
  booksLoading: false,
  booksError: null,
  stats: null,
  settings: initialSettings,
  lastRead: readJSON(LAST_READ_KEY, null),
  history: readJSON(HISTORY_KEY, []),
  searchHistory: readJSON(SEARCH_HISTORY_KEY, []),
  settingsOpen: false,
  menuOpen: false,
  toasts: [],
  health: null,
})

let booksPromise = null

export function ensureBooks(force = false) {
  if (store.books.length && !force) return Promise.resolve(store.books)
  if (booksPromise && !force) return booksPromise
  store.booksLoading = true
  store.booksError = null
  booksPromise = api
    .getBooks()
    .then((books) => {
      store.books = Array.isArray(books) ? books : []
      return store.books
    })
    .catch((error) => {
      store.booksError = error.message || '书卷加载失败'
      booksPromise = null
      throw error
    })
    .finally(() => {
      store.booksLoading = false
    })
  return booksPromise
}

export function ensureStats() {
  if (store.stats) return Promise.resolve(store.stats)
  return api
    .getStats()
    .then((stats) => {
      store.stats = stats
      return stats
    })
    .catch(() => null)
}

export function bookById(id) {
  return store.books.find((book) => Number(book.id) === Number(id)) || null
}

export const oldTestament = computed(() => store.books.filter((b) => b.testament === 'OT'))
export const newTestament = computed(() => store.books.filter((b) => b.testament === 'NT'))

export function saveSettings(patch) {
  store.settings = normalizeSettings({ ...store.settings, ...patch })
  writeJSON(SETTINGS_KEY, store.settings)
  applyTheme(store.settings.theme)
}

export function resetSettings() {
  saveSettings({ ...DEFAULT_SETTINGS })
}

export function recordRead(book, chapter) {
  if (!book) return
  const entry = {
    bookId: book.id,
    bookName: book.name,
    bookShortName: book.shortName,
    chapter: Number(chapter),
    at: new Date().toISOString(),
  }
  store.lastRead = entry
  writeJSON(LAST_READ_KEY, entry)
  const rest = store.history.filter((item) => !(item.bookId === entry.bookId && item.chapter === entry.chapter))
  store.history = [entry, ...rest].slice(0, 12)
  writeJSON(HISTORY_KEY, store.history)
}

export function addSearchHistory(query) {
  const q = String(query || '').trim()
  if (!q) return
  store.searchHistory = [q, ...store.searchHistory.filter((item) => item !== q)].slice(0, 10)
  writeJSON(SEARCH_HISTORY_KEY, store.searchHistory)
}

export function clearSearchHistory() {
  store.searchHistory = []
  writeJSON(SEARCH_HISTORY_KEY, [])
}

export function logReader(event, payload = {}) {
  logEvent(event, payload)
}

let toastSeed = 0
export function toast(message, type = 'success', duration = 2600) {
  const id = ++toastSeed
  store.toasts.push({ id, message, type })
  window.setTimeout(() => removeToast(id), duration)
  return id
}

export function removeToast(id) {
  const index = store.toasts.findIndex((item) => item.id === id)
  if (index >= 0) store.toasts.splice(index, 1)
}

export function toggleTheme() {
  const next = store.settings.theme === 'night' ? 'paper' : 'night'
  saveSettings({ theme: next })
}

export async function refreshHealth() {
  try {
    store.health = await api.getHealth()
  } catch {
    store.health = null
  }
  return store.health
}
