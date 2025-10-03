<template>
  <div class="toast-container" role="status" aria-live="polite">
    <transition-group name="toast" tag="ul">
      <li v-for="toast in toasts" :key="toast.id" class="toast" :class="`toast-${toast.type}`">
        <span class="toast-message">{{ toast.message }}</span>
        <button
          v-if="toast.action && toast.actionLabel"
          type="button"
          class="toast-action"
          @click="handleAction(toast)"
        >
          {{ toast.actionLabel }}
        </button>
        <button type="button" class="toast-close" @click="dismiss(toast.id)" aria-label="Close notification">
          ×
        </button>
      </li>
    </transition-group>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useToastStore, type ToastMessage } from '@/stores/toastStore'

const toastStore = useToastStore()
const { toasts } = storeToRefs(toastStore)

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
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  z-index: 1100;
  pointer-events: none;
}

.toast-container ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.toast {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-lg);
  color: var(--color-background);
  pointer-events: auto;
}

.toast-info {
  background-color: var(--color-info-600);
}

.toast-success {
  background-color: var(--color-success-600);
}

.toast-warning {
  background-color: var(--color-warning-600);
}

.toast-error {
  background-color: var(--color-error-600);
}

.toast-message {
  flex: 1;
}

.toast-action,
.toast-close {
  border: none;
  background: transparent;
  color: inherit;
  font-weight: var(--font-weight-medium);
  cursor: pointer;
}

.toast-action {
  text-decoration: underline;
}

.toast-close {
  font-size: var(--font-size-lg);
  line-height: 1;
  padding: 0 var(--space-1);
}

@media (max-width: 640px) {
  .toast-container {
    right: 50%;
    transform: translateX(50%);
    bottom: var(--space-4);
    width: calc(100% - var(--space-8));
  }

  .toast {
    width: 100%;
  }
}
</style>
