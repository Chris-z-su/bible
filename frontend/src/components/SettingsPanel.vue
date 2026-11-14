<script setup>
import { computed } from 'vue'
import { Check, Minus, Plus, RotateCcw, SlidersHorizontal, X } from 'lucide-vue-next'
import { resetSettings, saveSettings, store } from '../store'
import { COPY_PRESETS, DEFAULT_COPY_TEMPLATE, buildCopyText } from '../utils/format'

const themes = [
  { id: 'paper', name: '纸张', hint: '温暖明亮', color: '#f5f0e6', ink: '#2b241c' },
  { id: 'sepia', name: '米黄', hint: '护眼柔和', color: '#e9dfc6', ink: '#3a3021' },
  { id: 'night', name: '夜间', hint: '低光沉浸', color: '#16140f', ink: '#e9e1d2' },
]

const settings = computed(() => store.settings)

function setTheme(theme) {
  saveSettings({ theme })
}

function stepFont(delta) {
  const next = Math.min(26, Math.max(16, Number(settings.value.fontSize) + delta))
  saveSettings({ fontSize: next })
}

function close() {
  store.settingsOpen = false
}

function reset() {
  resetSettings()
}

const copyPresetName = computed(
  () => COPY_PRESETS.find((item) => item.id === settings.value.copyPreset)?.name || '自定义',
)

const copySample = [
  { verse: 1, text: '耶和华是我的牧者，我必不致缺乏。' },
  { verse: 2, text: '他使我躺卧在青草地上，领我在可安歇的水边。' },
]

const copyPreview = computed(() => buildCopyText('诗篇', 23, copySample, settings.value))

function applyCopyPreset(id) {
  if (id === 'standard') {
    saveSettings({ copyPreset: id, copyRef: true, copyNumbers: true, copyLayout: 'line', copyTemplate: '' })
  } else if (id === 'plain') {
    saveSettings({ copyPreset: id, copyRef: false, copyNumbers: false, copyLayout: 'line', copyTemplate: '' })
  } else if (id === 'markdown') {
    saveSettings({ copyPreset: id, copyRef: true, copyNumbers: true, copyLayout: 'line', copyTemplate: '' })
  } else {
    const wasCustom = settings.value.copyPreset === 'custom'
    saveSettings(
      wasCustom
        ? { copyPreset: 'custom' }
        : { copyPreset: 'custom', copyRef: true, copyNumbers: true, copyLayout: 'line' },
    )
  }
}

function setCopyOption(patch) {
  saveSettings({ copyPreset: 'custom', ...patch })
}

function setCopyTemplate(event) {
  saveSettings({ copyPreset: 'custom', copyTemplate: event.target.value })
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="store.settingsOpen" class="popover-backdrop" @click="close" />
    </Transition>
    <Transition name="pop">
      <aside v-if="store.settingsOpen" class="settings-panel" role="dialog" aria-label="阅读设置">
        <header class="settings-head">
          <div>
            <p class="settings-eyebrow"><SlidersHorizontal :size="13" /> 阅读体验</p>
            <h2>阅读设置</h2>
          </div>
          <button class="icon-btn" type="button" aria-label="关闭设置" @click="close">
            <X :size="18" />
          </button>
        </header>

        <section class="settings-section">
          <h3>主题</h3>
          <div class="theme-grid">
            <button
              v-for="theme in themes"
              :key="theme.id"
              type="button"
              class="theme-card"
              :class="{ active: settings.theme === theme.id }"
              :aria-pressed="settings.theme === theme.id"
              @click="setTheme(theme.id)"
            >
              <span class="theme-swatch" :style="{ background: theme.color, color: theme.ink }">Aa</span>
              <span class="theme-name">{{ theme.name }}</span>
              <span class="theme-hint">{{ theme.hint }}</span>
              <Check v-if="settings.theme === theme.id" class="theme-check" :size="14" />
            </button>
          </div>
        </section>

        <section class="settings-section">
          <div class="settings-row">
            <h3>正文字号</h3>
            <div class="stepper">
              <button type="button" aria-label="减小字号" @click="stepFont(-1)"><Minus :size="14" /></button>
              <span>{{ settings.fontSize }} px</span>
              <button type="button" aria-label="增大字号" @click="stepFont(1)"><Plus :size="14" /></button>
            </div>
          </div>
          <input
            class="range"
            type="range"
            min="16"
            max="26"
            step="1"
            :value="settings.fontSize"
            aria-label="正文字号"
            @input="saveSettings({ fontSize: Number($event.target.value) })"
          />
        </section>

        <section class="settings-section">
          <div class="settings-row">
            <h3>行间距离</h3>
            <span class="settings-value">{{ Number(settings.lineHeight).toFixed(1) }}</span>
          </div>
          <input
            class="range"
            type="range"
            min="1.6"
            max="2.6"
            step="0.05"
            :value="settings.lineHeight"
            aria-label="行间距离"
            @input="saveSettings({ lineHeight: Number($event.target.value) })"
          />
        </section>

        <section class="settings-section settings-toggles">
          <label class="toggle-row">
            <span>
              <strong>段落连读</strong>
              <small>按段落连续排版，更接近纸质书</small>
            </span>
            <button
              type="button"
              class="switch"
              :class="{ on: settings.paragraph }"
              role="switch"
              :aria-checked="settings.paragraph"
              @click="saveSettings({ paragraph: !settings.paragraph })"
            >
              <span />
            </button>
          </label>
          <label class="toggle-row">
            <span>
              <strong>显示节号</strong>
              <small>在经文前显示节数，便于引用</small>
            </span>
            <button
              type="button"
              class="switch"
              :class="{ on: settings.showVerseNumbers }"
              role="switch"
              :aria-checked="settings.showVerseNumbers"
              @click="saveSettings({ showVerseNumbers: !settings.showVerseNumbers })"
            >
              <span />
            </button>
          </label>
          <label class="toggle-row">
            <span>
              <strong>减少动效</strong>
              <small>关闭页面过渡与滚动动画</small>
            </span>
            <button
              type="button"
              class="switch"
              :class="{ on: settings.reduceMotion }"
              role="switch"
              :aria-checked="settings.reduceMotion"
              @click="saveSettings({ reduceMotion: !settings.reduceMotion })"
            >
              <span />
            </button>
          </label>
        </section>

        <section class="settings-section copy-section">
          <div class="settings-row">
            <h3>经文复制</h3>
            <span class="settings-value">{{ copyPresetName }}</span>
          </div>
          <div class="copy-presets" role="group" aria-label="复制样式">
            <button
              v-for="preset in COPY_PRESETS"
              :key="preset.id"
              type="button"
              class="copy-preset"
              :class="{ active: settings.copyPreset === preset.id }"
              :data-copy-preset="preset.id"
              :aria-pressed="settings.copyPreset === preset.id"
              @click="applyCopyPreset(preset.id)"
            >
              <strong>{{ preset.name }}</strong>
              <small>{{ preset.hint }}</small>
            </button>
          </div>

          <div v-if="settings.copyPreset === 'custom'" class="copy-custom">
            <div class="copy-option">
              <span>包含引用</span>
              <button
                type="button"
                class="switch"
                :class="{ on: settings.copyRef }"
                role="switch"
                :aria-checked="settings.copyRef"
                aria-label="复制时包含引用"
                @click="setCopyOption({ copyRef: !settings.copyRef })"
              >
                <span />
              </button>
            </div>
            <div class="copy-option">
              <span>包含节号</span>
              <button
                type="button"
                class="switch"
                :class="{ on: settings.copyNumbers }"
                role="switch"
                :aria-checked="settings.copyNumbers"
                aria-label="复制时包含节号"
                @click="setCopyOption({ copyNumbers: !settings.copyNumbers })"
              >
                <span />
              </button>
            </div>
            <div class="copy-option">
              <span>分节方式</span>
              <select
                class="copy-select"
                :value="settings.copyLayout"
                aria-label="复制时分节方式"
                @change="setCopyOption({ copyLayout: $event.target.value })"
              >
                <option value="line">每节一行</option>
                <option value="blank">空行分隔</option>
                <option value="inline">连续排版</option>
              </select>
            </div>
            <textarea
              class="copy-template"
              :value="settings.copyTemplate"
              rows="3"
              :placeholder="DEFAULT_COPY_TEMPLATE"
              aria-label="自定义复制模板"
              @input="setCopyTemplate"
            />
            <p class="copy-placeholders">
              模板优先；留空则按上方选项生成。支持 <code>{ref}</code> <code>{book}</code>
              <code>{chapter}</code> <code>{range}</code> <code>{count}</code> <code>{verses}</code>
              <code>{text}</code> <code>{version}</code>。
            </p>
          </div>

          <div class="copy-preview">
            <div class="copy-preview-head">
              <span>复制预览</span>
              <small>{{ settings.copyPreset === 'custom' && settings.copyTemplate ? '模板' : '选项' }}</small>
            </div>
            <pre>{{ copyPreview }}</pre>
          </div>
        </section>

        <footer class="settings-foot">
          <span>设置保存在本机浏览器</span>
          <button type="button" class="text-btn" @click="reset">
            <RotateCcw :size="13" /> 恢复默认
          </button>
        </footer>
      </aside>
    </Transition>
  </Teleport>
</template>
