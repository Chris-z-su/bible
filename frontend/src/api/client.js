const API_BASE = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(message, code = -1, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

function buildUrl(path, params) {
  const url = new URL(`${API_BASE}${path}`, window.location.origin)
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value))
      }
    })
  }
  return url.toString()
}

async function request(path, { method = 'GET', params, body, timeout = 20000, keepalive = false } = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  try {
    const response = await fetch(buildUrl(path, params), {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
      keepalive,
    })
    const raw = await response.text()
    let payload = null
    try {
      payload = raw ? JSON.parse(raw) : null
    } catch {
      payload = null
    }
    if (!response.ok || (payload && payload.code !== 0)) {
      const message = payload?.message || `请求失败（HTTP ${response.status}）`
      throw new ApiError(message, payload?.code ?? response.status, response.status)
    }
    return payload?.data ?? null
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new ApiError('请求超时，请稍后重试', -2, 0)
    }
    if (error instanceof ApiError) throw error
    throw new ApiError('无法连接服务器，请确认后端已启动', -3, 0)
  } finally {
    clearTimeout(timer)
  }
}

export const api = {
  getBooks: () => request('/books'),
  getBookChapters: (bookId) => request(`/books/${bookId}/chapters`),
  getChapter: (bookId, chapter) => request(`/chapters/${bookId}/${chapter}`),
  search: (params) => request('/search', { params }),
  parseReference: (q, bookId) => request('/reference/parse', { params: { q, bookId } }),
  getStats: () => request('/stats'),
  getHealth: () => request('/health'),
  searchLogs: (params) => request('/logs/search', { params }),
  postLog: (payload) => request('/logs', { method: 'POST', body: payload, timeout: 4000, keepalive: true }),
}

/** 行为埋点：不阻塞界面，失败静默。 */
export function logEvent(event, payload = {}) {
  const body = {
    event,
    level: payload.level || 'INFO',
    message: payload.message || '',
    detail: payload.detail || null,
    bookId: payload.bookId ?? null,
    chapter: payload.chapter ?? null,
    verse: payload.verse ?? null,
    query: payload.query ?? null,
  }
  api.postLog(body).catch(() => {})
}
