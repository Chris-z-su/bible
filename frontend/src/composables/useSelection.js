import { reactive } from 'vue'
import { buildCopyText, copyText, verseRangeLabel } from '../utils/format'
import { store } from '../store'

export const selection = reactive({
  active: false,
  bookId: null,
  bookName: '',
  bookShortName: '',
  chapter: null,
  verses: [],
  anchor: null,
})

export function clearSelection() {
  selection.active = false
  selection.bookId = null
  selection.bookName = ''
  selection.bookShortName = ''
  selection.chapter = null
  selection.verses = []
  selection.anchor = null
}

function apply(book, chapter, verses, anchor) {
  const sorted = [...verses].sort((a, b) => a.verse - b.verse)
  selection.active = sorted.length > 0
  selection.bookId = book.id
  selection.bookName = book.name
  selection.bookShortName = book.shortName
  selection.chapter = Number(chapter)
  selection.verses = sorted
  selection.anchor = anchor ?? sorted[0]?.verse ?? null
}

export function selectSingle(book, chapter, verse) {
  apply(book, chapter, [verse], verse.verse)
}

export function toggleVerse(book, chapter, verse) {
  if (!selection.active || selection.bookId !== book.id || selection.chapter !== Number(chapter)) {
    selectSingle(book, chapter, verse)
    return
  }
  const exists = selection.verses.some((item) => item.verse === verse.verse)
  const next = exists
    ? selection.verses.filter((item) => item.verse !== verse.verse)
    : [...selection.verses, verse]
  if (!next.length) {
    clearSelection()
    return
  }
  apply(book, chapter, next, verse.verse)
}

export function selectRange(book, chapter, allVerses, fromVerse, toVerse) {
  const start = Math.min(fromVerse, toVerse)
  const end = Math.max(fromVerse, toVerse)
  const range = allVerses.filter((item) => item.verse >= start && item.verse <= end)
  apply(book, chapter, range, toVerse)
}

/** 统一处理点击：Shift 连选、Ctrl/Cmd 多选、普通单选。 */
export function handleVerseClick(event, book, chapter, verse, allVerses) {
  const nativeSelection = window.getSelection()
  if (nativeSelection && !nativeSelection.isCollapsed) return

  if (event.shiftKey && selection.active && selection.bookId === book.id && selection.chapter === Number(chapter)) {
    selectRange(book, chapter, allVerses, selection.anchor ?? verse.verse, verse.verse)
    return
  }
  if (event.metaKey || event.ctrlKey) {
    toggleVerse(book, chapter, verse)
    return
  }
  if (
    selection.active &&
    selection.bookId === book.id &&
    selection.chapter === Number(chapter) &&
    selection.verses.length === 1 &&
    selection.verses[0].verse === verse.verse
  ) {
    clearSelection()
    return
  }
  selectSingle(book, chapter, verse)
}

export function selectionLabel() {
  if (!selection.active) return ''
  return `${selection.bookName} ${selection.chapter}:${verseRangeLabel(selection.verses)}`
}

export async function copySelectionText() {
  if (!selection.active) return false
  return copyText(buildCopyText(selection.bookName, selection.chapter, selection.verses, store.settings))
}

export function selectionLink() {
  if (!selection.active) return ''
  const first = selection.verses[0].verse
  const last = selection.verses[selection.verses.length - 1].verse
  const versePart = first === last ? String(first) : `${first}-${last}`
  const base = `${window.location.origin}${window.location.pathname}`
  return `${base}#/read/${selection.bookId}/${selection.chapter}?verse=${versePart}`
}
