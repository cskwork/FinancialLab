<template>
  <div class="toast-container" role="status" aria-live="polite">
    <transition-group name="toast" tag="ul">
      <li v-for="toast in toasts" :key="toast.id" class="toast" :class="`toast-${toast.type}`">
        <span class="toast-kind">{{ t(`toast.${toast.type}`) }}</span>
        <span class="toast-message">{{ toast.message }}</span>
        <button
          v-if="toast.action && toast.actionLabel"
          type="button"
          class="toast-action"
          @click="handleAction(toast)"
        >
          {{ toast.actionLabel }}
        </button>
        <button type="button" class="toast-close" @click="dismiss(toast.id)" :aria-label="t('common.close')">
          <X aria-hidden="true" />
        </button>
      </li>
    </transition-group>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useToastStore, type ToastMessage } from '@/stores/toastStore'

const toastStore = useToastStore()
const { toasts } = storeToRefs(toastStore)

const { t } = useI18n()

function dismiss(id: string) {
  toastStore.removeToast(id)
}

function handleAction(toast: ToastMessage) {
  toast.action?.()
  dismiss(toast.id)
}
</script>

<style scoped>
.toast-container {
  position: fixed;
  right: var(--space-6);
  bottom: var(--space-6);
  z-index: 1100;
  pointer-events: none;
}

.toast-container ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-out);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

/* a slip torn from the pad */
.toast {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: min(360px, 100%);
  max-width: 440px;
  padding: var(--space-3) var(--space-3) var(--space-3) var(--space-4);
  background-color: var(--paper-raised);
  color: var(--ink);
  border: 1.5px solid var(--rule-strong);
  box-shadow: var(--shadow-lg);
  pointer-events: auto;
  font-size: var(--text-sm);
}

.toast-kind {
  flex: none;
  font-family: var(--font-label);
  font-weight: 700;
  font-size: var(--text-xs);
  letter-spacing: var(--tracking-label);
  text-transform: uppercase;
  padding: 1px 6px;
  border: 1px solid currentColor;
}

.toast-success .toast-kind {
  background-color: var(--highlight);
  color: var(--highlight-ink);
  border-color: var(--highlight);
}

.toast-info .toast-kind {
  color: var(--blue-pencil);
}

.toast-warning .toast-kind {
  color: var(--color-warning-600);
}

.toast-error {
  border-color: var(--red-pencil);
}

.toast-error .toast-kind {
  color: var(--red-pencil);
}

.toast-message {
  flex: 1;
}

.toast-action {
  font-family: var(--font-label);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  text-decoration: underline;
}

.toast-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--ink-2);
}

.toast-close:hover {
  color: var(--ink);
  background-color: var(--paper-sunk);
}

.toast-close svg {
  width: 16px;
  height: 16px;
}

@media (max-width: 640px) {
  .toast-container {
    right: var(--space-3);
    left: var(--space-3);
    bottom: var(--space-3);
  }

  .toast {
    min-width: 0;
    max-width: none;
  }
}
</style>
