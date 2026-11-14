<script setup>
import { CheckCircle2, Info, AlertTriangle, XCircle } from 'lucide-vue-next'
import { removeToast, store } from '../store'

const icons = {
  success: CheckCircle2,
  info: Info,
  error: XCircle,
  warning: AlertTriangle,
}
</script>

<template>
  <Teleport to="body">
    <TransitionGroup name="toast" tag="div" class="toast-host" aria-live="polite">
      <div
        v-for="item in store.toasts"
        :key="item.id"
        class="toast"
        :class="`toast-${item.type}`"
        role="status"
        @click="removeToast(item.id)"
      >
        <component :is="icons[item.type] || Info" :size="17" :stroke-width="1.9" />
        <span>{{ item.message }}</span>
      </div>
    </TransitionGroup>
  </Teleport>
</template>
