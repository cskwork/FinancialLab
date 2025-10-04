<!--
  공통 Button 컴포넌트
  재사용 가능한 버튼 컴포넌트 (향후 확장 예정)
-->
<template>
  <button
    :type="type"
    :class="['btn', `btn-${variant}`, `btn-${size}`, { 'btn-loading': loading }]"
    :disabled="disabled || loading"
    @click="handleClick"
  >
    <span v-if="loading" class="btn-spinner"></span>
    <slot></slot>
  </button>
</template>

<script setup lang="ts">
interface Props {
  type?: 'button' | 'submit' | 'reset'
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
  disabled?: boolean
}

interface Emits {
  (e: 'click', event: MouseEvent): void
}

withDefaults(defineProps<Props>(), {
  type: 'button',
  variant: 'primary',
  size: 'md',
  loading: false,
  disabled: false
})

const emit = defineEmits<Emits>()

function handleClick(event: MouseEvent) {
  emit('click', event)
}
</script>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: none;
  border-radius: var(--border-radius-md);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Variants */
.btn-primary {
  background-color: var(--color-primary-600);
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background-color: var(--color-primary-700);
}

.btn-secondary {
  background-color: var(--color-gray-200);
  color: var(--color-text);
}

.btn-secondary:hover:not(:disabled) {
  background-color: var(--color-gray-300);
}

.btn-danger {
  background-color: var(--color-error-600);
  color: #fff;
}

.btn-danger:hover:not(:disabled) {
  background-color: var(--color-error-700);
}

.btn-ghost {
  background-color: transparent;
  color: var(--color-primary-600);
  border: 1px solid var(--color-primary-600);
}

.btn-ghost:hover:not(:disabled) {
  background-color: var(--color-primary-50);
}

/* Sizes */
.btn-sm {
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-sm);
}

.btn-md {
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-base);
}

.btn-lg {
  padding: var(--space-4) var(--space-6);
  font-size: var(--font-size-lg);
}

/* Loading state */
.btn-loading {
  position: relative;
  color: transparent;
}

.btn-spinner {
  position: absolute;
  width: 16px;
  height: 16px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
