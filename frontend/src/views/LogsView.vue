<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  Activity,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Clock,
  Database,
  Info,
  Loader2,
  RefreshCw,
  Search,
  Server,
  XCircle,
} from 'lucide-vue-next'
import { api } from '../api/client'
import { refreshHealth, store } from '../store'
import { formatTime } from '../utils/format'

const query = ref('')
const level = ref('')
const event = ref('')
const range = ref('24h')
const page = ref(1)
const size = 20
const data = ref(null)
const loading = ref(false)
const error = ref('')
const autoRefresh = ref(false)
const lastUpdated = ref(null)
let timer = null
let timerToken = null

const levels = [
  { value: '', label: '全部级别' },
  { value: 'INFO', label: 'INFO' },
  { value: 'WARN', label: 'WARN' },
  { value: 'ERROR', label: 'ERROR' },
]

const events = [
  { value: '', label: '全部事件' },
  { value: 'read', label: '阅读' },
  { value: 'search', label: '搜索' },
  { value: 'copy', label: '复制' },
  { value: 'jump', label: '跳转' },
  { value: 'page_view', label: '访问' },
  { value: 'api', label: '接口' },
  { value: 'error', label: '错误' },
]

const ranges = [
  { value: '1h', label: '最近 1 小时' },
  { value: '24h', label: '最近 24 小时' },
  { value: '7d', label: '最近 7 天' },
  { value: 'all', label: '全部时间' },
]

const stats = computed(() => data.value?.stats || { levels: {}, events: {} })
const totalLogs = computed(() => {
  const value = stats.value.levels || {}
  return Object.values(value).reduce((sum, n) => sum + Number(n || 0), 0)
})
const levelCards = computed(() => [
  { id: 'INFO', label: 'INFO', icon: Info, count: stats.value.levels?.INFO || 0, tone: 'info' },
  { id: 'WARN', label: 'WARN', icon: AlertTriangle, count: stats.value.levels?.WARN || 0, tone: 'warn' },
  { id: 'ERROR', label: 'ERROR', icon: XCircle, count: stats.value.levels?.ERROR || 0, tone: 'error' },
])
const eventStats = computed(() =>
  Object.entries(stats.value.events || {})
    .sort((a, b) => Number(b[1]) - Number(a[1]))
    .slice(0, 6)
    .map(([key, value]) => ({ key, label: eventLabel(key), count: value })),
)
const totalPages = computed(() => Math.max(1, Math.ceil((data.value?.total || 0) / size)))

const health = computed(() => store.health)

function eventLabel(value) {
  const map = {
    read: '阅读', search: '搜索', copy: '复制', jump: '跳转',
    page_view: '访问', api: '接口', error: '错误', theme: '主题', font: '字号',
  }
  return map[value] || value
}

function displayMessage(item) {
  const message = String(item?.message || '')
  if (item?.event === 'api') {
    return message.replace(/\s+\d{3}\s+\d+ms$/, '')
  }
  return message || '—'
}

function fromISO() {
  if (range.value === 'all') return undefined
  const hours = range.value === '1h' ? 1 : range.value === '7d' ? 168 : 24
  return new Date(Date.now() - hours * 3600 * 1000).toISOString()
}

async function loadLogs(targetPage = page.value, silent = false) {
  if (!silent) loading.value = true
  error.value = ''
  try {
    const params = { page: targetPage, size }
    if (query.value.trim()) params.q = query.value.trim()
    if (level.value) params.level = level.value
    if (event.value) params.event = event.value
    const from = fromISO()
    if (from) params.from = from
    const result = await api.searchLogs(params)
    data.value = result
    page.value = targetPage
    lastUpdated.value = new Date().toISOString()
  } catch (err) {
    error.value = err.message || '日志检索失败'
  } finally {
    if (!silent) loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  loadLogs(1)
  refreshHealth()
}

function submitSearch() {
  applyFilters()
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.value) return
  loadLogs(target)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function startTimer() {
  stopTimer()
  timer = window.setInterval(() => {
    if (document.visibilityState === 'visible') loadLogs(page.value, true)
  }, 8000)
}

function stopTimer() {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
}

watch(autoRefresh, (enabled) => {
  if (enabled) startTimer()
  else stopTimer()
})

onMounted(() => {
  loadLogs(1)
  refreshHealth()
  timerToken = window.setInterval(() => refreshHealth(), 60000)
})

onUnmounted(() => {
  stopTimer()
  if (timerToken) window.clearInterval(timerToken)
})
</script>

<template>
  <div class="logs-page">
    <div class="logs-container">
      <header class="logs-head">
        <div>
          <p class="section-eyebrow"><Activity :size="14" /> OBSERVABILITY</p>
          <h1>系统日志</h1>
          <p class="logs-sub">阅读行为与接口访问日志，由 Elasticsearch 索引，支持全文检索与级别筛选。</p>
        </div>
        <div class="health-pills" aria-label="依赖服务状态">
          <span class="health-pill" :class="health?.mysql ? 'ok' : 'bad'">
            <Database :size="14" /> MySQL
          </span>
          <span class="health-pill" :class="health?.redis ? 'ok' : 'bad'">
            <Server :size="14" /> Redis
          </span>
          <span class="health-pill" :class="health?.elasticsearch ? 'ok' : 'bad'">
            <Activity :size="14" /> Elasticsearch
          </span>
        </div>
      </header>

      <div class="log-stats">
        <article class="stat-card stat-total">
          <p>日志总数</p>
          <strong>{{ totalLogs.toLocaleString() }}</strong>
          <span>当前筛选范围</span>
        </article>
        <article v-for="card in levelCards" :key="card.id" class="stat-card" :class="`stat-${card.tone}`">
          <p>{{ card.label }}</p>
          <strong>{{ card.count.toLocaleString() }}</strong>
          <span><component :is="card.icon" :size="13" /> {{ card.label === 'INFO' ? '常规记录' : card.label === 'WARN' ? '需要留意' : '异常事件' }}</span>
        </article>
      </div>

      <div v-if="eventStats.length" class="event-stats">
        <span class="event-stats-label">事件分布</span>
        <span v-for="item in eventStats" :key="item.key" class="event-stat">
          {{ item.label }} <b>{{ item.count }}</b>
        </span>
      </div>

      <div class="log-filters">
        <form class="log-search" @submit.prevent="submitSearch">
          <Search :size="17" />
          <input v-model="query" type="search" placeholder="搜索日志内容、接口路径、关键词" aria-label="搜索日志" />
          <button type="submit" class="btn primary" :disabled="loading">检索</button>
        </form>
        <div class="filter-row">
          <select v-model="level" aria-label="日志级别" @change="applyFilters">
            <option v-for="item in levels" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
          <select v-model="event" aria-label="事件类型" @change="applyFilters">
            <option v-for="item in events" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
          <select v-model="range" aria-label="时间范围" @change="applyFilters">
            <option v-for="item in ranges" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
          <span class="filter-spacer" />
          <button type="button" class="tool-btn compact" @click="loadLogs(page)">
            <RefreshCw :size="15" :class="{ spin: loading }" />
            刷新
          </button>
          <label class="auto-refresh">
            <input v-model="autoRefresh" type="checkbox" />
            <span>实时</span>
          </label>
        </div>
      </div>

      <div v-if="data?.degraded" class="degraded-banner">
        <AlertTriangle :size="16" />
        Elasticsearch 暂不可用，当前展示后端内存缓冲中的最近日志，检索能力受限。
      </div>

      <div v-if="error" class="logs-error">
        <p>{{ error }}</p>
        <button type="button" class="btn ghost" @click="loadLogs(page)">重试</button>
      </div>

      <div v-else-if="loading && !data" class="logs-loading">
        <Loader2 class="spin" :size="20" /> 正在检索日志……
      </div>

      <template v-else-if="data">
        <div class="log-table-wrap">
          <div class="log-table">
            <div class="log-table-head">
              <span>时间</span>
              <span>级别</span>
              <span>事件</span>
              <span>内容</span>
              <span>请求 / 耗时</span>
            </div>

            <div v-if="!data.items?.length" class="log-empty">
              <p>暂无日志</p>
              <span>打开经文、搜索或复制经文后，这里会出现对应记录。</span>
            </div>

            <article v-for="(item, index) in data.items" :key="`${item.timestamp}-${index}`" class="log-row" :class="`level-${(item.level || 'info').toLowerCase()}`">
              <div class="log-time">
                <Clock :size="12" />
                <span>{{ formatTime(item.timestamp) }}</span>
              </div>
              <div><span class="level-badge" :class="(item.level || 'info').toLowerCase()">{{ item.level || 'INFO' }}</span></div>
              <div><span class="event-badge">{{ eventLabel(item.event) }}</span></div>
              <div class="log-message">
                <p>{{ displayMessage(item) }}</p>
                <small v-if="item.query">{{ item.event === 'api' ? 'Query' : '关键词' }}：{{ item.query }}</small>
                <small v-if="item.detail">{{ item.detail }}</small>
                <small v-if="item.event === 'api' && item.status" class="api-inline-meta">
                  HTTP {{ item.status }} · {{ item.durationMs }}ms
                </small>
              </div>
              <div class="log-request">
                <span v-if="item.method" class="log-path">{{ item.method }} {{ item.path }}</span>
                <span v-else class="log-path">—</span>
                <span v-if="item.status" class="log-status" :class="{ bad: item.status >= 400 }">{{ item.status }}</span>
                <span v-if="item.durationMs !== null && item.durationMs !== undefined" class="log-duration">{{ item.durationMs }}ms</span>
              </div>
            </article>
          </div>
        </div>

        <div class="logs-foot">
          <p>
            共 {{ data.total.toLocaleString() }} 条
            <span v-if="lastUpdated">· 更新于 {{ formatTime(lastUpdated).slice(11) }}</span>
            <span v-if="data.degraded" class="degraded-text">· 降级模式</span>
          </p>
          <nav v-if="totalPages > 1" class="pagination" aria-label="日志分页">
            <button type="button" class="page-btn" :disabled="page <= 1" @click="goPage(page - 1)">
              <ChevronLeft :size="16" />
            </button>
            <span class="page-current">{{ page }} / {{ totalPages }}</span>
            <button type="button" class="page-btn" :disabled="page >= totalPages" @click="goPage(page + 1)">
              <ChevronRight :size="16" />
            </button>
          </nav>
        </div>
      </template>
    </div>
  </div>
</template>
