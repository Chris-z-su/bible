import { HOT_VERSES } from '../data/dailyVerses.js'

/** 按本地时区生成日期键：2026-11-13 */
export function formatDateKey(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 卡片上展示的完整年月日：2026年11月13日 */
export function formatDateLabel(date = new Date()) {
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

/** FNV-1a 32 位哈希，用日期作为种子，保证同一天稳定 */
function hashString(input) {
  let hash = 2166136261
  for (let i = 0; i < input.length; i += 1) {
    hash ^= input.charCodeAt(i)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

/** 按日期从热门金句库中选一条；允许不同日期重复选中同一节；previewIndex 可从 0 起指定条目用于预览 */
export function pickDailyVerse(date = new Date(), previewIndex) {
  const dateKey = formatDateKey(date)
  const forced = Number(previewIndex)
  const index = Number.isInteger(forced) && forced >= 0 && forced < HOT_VERSES.length
    ? forced
    : hashString(`bible-daily:${dateKey}`) % HOT_VERSES.length
  return {
    dateKey,
    index,
    ref: HOT_VERSES[index],
    size: HOT_VERSES.length,
  }
}
