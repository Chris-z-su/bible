/** 格式化经文引用：约翰福音 3:16 或 约翰福音 3:16-18 */
export function formatReference(bookName, chapter, verseStart, verseEnd) {
  if (!bookName || !chapter) return ''
  const start = Number(verseStart)
  const end = Number(verseEnd)
  if (start && end && end > start) return `${bookName} ${chapter}:${start}-${end}`
  if (start) return `${bookName} ${chapter}:${start}`
  return `${bookName} ${chapter}`
}

export function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes(),
  )}:${pad(date.getSeconds())}`
}

export function timeAgo(value) {
  if (!value) return ''
  const time = new Date(value).getTime()
  if (Number.isNaN(time)) return ''
  const diff = Math.max(0, Date.now() - time)
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < day * 7) return `${Math.floor(diff / day)} 天前`
  return formatTime(value).slice(0, 10)
}

/** 把经文拆成普通文本 / 括号注释片段，用于括号内文字略小展示 */
export function splitParentheses(text) {
  const source = String(text ?? '')
  const parts = []
  let buffer = ''
  let depth = 0
  for (const char of source) {
    const isOpen = char === '（' || char === '('
    const isClose = char === '）' || char === ')'
    if (isOpen) {
      if (depth === 0 && buffer) {
        parts.push({ text: buffer, paren: false })
        buffer = ''
      }
      depth += 1
      buffer += char
    } else if (isClose && depth > 0) {
      buffer += char
      depth -= 1
      if (depth === 0) {
        parts.push({ text: buffer, paren: true })
        buffer = ''
      }
    } else {
      buffer += char
    }
  }
  if (buffer) parts.push({ text: buffer, paren: depth > 0 })
  return parts
}

/** 把高亮区间转换成可安全渲染的分段 */
export function splitHighlights(text, highlights) {
  const source = String(text ?? '')
  if (!Array.isArray(highlights) || !highlights.length) {
    return [{ text: source, mark: false }]
  }
  const ranges = highlights
    .filter((range) => Array.isArray(range) && range.length === 2)
    .map(([start, end]) => [Math.max(0, start), Math.min(source.length, end)])
    .filter(([start, end]) => end > start)
    .sort((a, b) => a[0] - b[0])

  const merged = []
  for (const [start, end] of ranges) {
    const last = merged[merged.length - 1]
    if (last && start <= last[1]) {
      last[1] = Math.max(last[1], end)
    } else {
      merged.push([start, end])
    }
  }

  const parts = []
  let cursor = 0
  for (const [start, end] of merged) {
    if (start > cursor) parts.push({ text: source.slice(cursor, start), mark: false })
    parts.push({ text: source.slice(start, end), mark: true })
    cursor = end
  }
  if (cursor < source.length) parts.push({ text: source.slice(cursor), mark: false })
  return parts
}

export async function copyText(text) {
  const value = String(text ?? '')
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(value)
      return true
    }
  } catch {
    /* 回退 */
  }
  try {
    const textarea = document.createElement('textarea')
    textarea.value = value
    textarea.setAttribute('readonly', '')
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    textarea.style.top = '-9999px'
    document.body.appendChild(textarea)
    textarea.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(textarea)
    return ok
  } catch {
    return false
  }
}

export function verseRangeLabel(verses) {
  if (!verses?.length) return ''
  const sorted = [...verses].sort((a, b) => a.verse - b.verse)
  const first = sorted[0].verse
  const last = sorted[sorted.length - 1].verse
  return first === last ? `${first}` : `${first}-${last}`
}

export const DEFAULT_COPY_TEMPLATE = '{ref}\n\n{verses}'

export const COPY_PRESETS = [
  { id: 'standard', name: '标准引用', hint: '引用 + 节号' },
  { id: 'plain', name: '纯经文', hint: '无引用 / 节号' },
  { id: 'markdown', name: 'Markdown', hint: '引用块' },
  { id: 'custom', name: '自定义', hint: '模板 / 选项' },
]

/** 可选的复制配置字段（与 store.settings 中的 copy* 字段对应） */
/**
 * 生成复制文本。
 *
 * options:
 *  - copyPreset: standard | plain | markdown | custom
 *  - copyNumbers: 是否包含节号
 *  - copyRef: 是否包含引用行（standard/plain/markdown 生效）
 *  - copyLayout: line | blank | inline
 *  - copyTemplate: 自定义模板，支持 {ref} {book} {chapter} {range} {count} {verses} {text} {version}
 *  - wholeChapter: 复制整章时 {ref} 使用「约翰福音 第 3 章（和合本）」
 */
export function buildCopyText(bookName, chapter, verses, options = {}) {
  const list = [...(verses || [])]
    .filter((item) => item && Number(item.verse) > 0)
    .sort((a, b) => Number(a.verse) - Number(b.verse))
  if (!list.length) return ''

  const opts = {
    copyPreset: 'standard',
    copyNumbers: true,
    copyRef: true,
    copyLayout: 'line',
    copyTemplate: '',
    wholeChapter: false,
    ...options,
  }

  const first = Number(list[0].verse)
  const last = Number(list[list.length - 1].verse)
  const range = first === last ? String(first) : `${first}-${last}`
  const ref = opts.wholeChapter
    ? `${bookName} 第 ${chapter} 章（和合本）`
    : `${bookName} ${chapter}:${range}`

  const verseLines = list.map((item) => {
    const text = String(item.text || '').trim()
    return opts.copyNumbers ? `${item.verse} ${text}` : text
  })

  let block
  if (opts.copyLayout === 'blank') block = verseLines.join('\n\n')
  else if (opts.copyLayout === 'inline') block = verseLines.join(' ')
  else block = verseLines.join('\n')

  if (opts.copyPreset === 'markdown') {
    const quoted = block
      .split('\n')
      .map((line) => (line ? `> ${line}` : '>'))
      .join('\n')
    const header = opts.copyRef ? `> **${ref}**\n>\n` : ''
    return `${header}${quoted}`.trim()
  }

  if (opts.copyPreset === 'custom') {
    const rawTemplate = String(opts.copyTemplate || '').trim()
    if (rawTemplate) {
      const template = rawTemplate.replace(/\\n/g, '\n')
      const values = {
        ref: opts.copyRef ? ref : '',
        book: bookName,
        chapter: String(chapter),
        range: opts.wholeChapter ? '全章' : range,
        count: String(list.length),
        version: '和合本',
        verses: block,
        text: list.map((item) => String(item.text || '').trim()).join(' '),
      }
      return template
        .replace(/\{(ref|book|chapter|range|count|version|verses|text)\}/g, (_, key) => values[key] ?? '')
        .trim()
    }
    // 模板留空时按下方选项生成，和标准样式共用同一套逻辑
  }

  const header = opts.copyRef ? ref : ''
  return [header, block].filter(Boolean).join('\n\n')
}
