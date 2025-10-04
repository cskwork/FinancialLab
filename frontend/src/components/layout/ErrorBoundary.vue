<template>
  <div>
    <slot v-if="!hasError" />
    <div v-else class="error-fallback" role="alert">
      <h1>문제가 발생했습니다</h1>
      <p class="message">{{ errorMessage }}</p>
      <div class="actions">
        <button type="button" class="btn-retry" @click="handleRetry">다시 시도</button>
        <button type="button" class="btn-home" @click="goHome">대시보드로 이동</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'

const hasError = ref(false)
const errorMessage = ref('알 수 없는 오류가 발생했습니다.')
const router = useRouter()
const toast = useToast()

function resetError() {
  hasError.value = false
  errorMessage.value = '알 수 없는 오류가 발생했습니다.'
}

function handleRetry() {
  resetError()
  toast.info('화면을 다시 불러왔습니다')
}

function goHome() {
  resetError()
  router.push('/dashboard')
}

onErrorCaptured((err) => {
  hasError.value = true
  errorMessage.value = err instanceof Error ? err.message : '예상치 못한 오류입니다.'
  console.error('[ErrorBoundary]', err)
  toast.error('오류가 발생했습니다. 잠시 후 다시 시도하세요.')
  return false
})
</script>

<style scoped>
.error-fallback {
  min-height: 50vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: var(--space-4);
  text-align: center;
  padding: var(--space-8);
}

.error-fallback h1 {
  margin: 0;
  font-size: var(--font-size-3xl);
  color: var(--color-error-600);
}

.message {
  color: var(--color-text-muted);
  max-width: 480px;
}

.actions {
  display: flex;
  gap: var(--space-4);
  flex-wrap: wrap;
  justify-content: center;
}

.btn-retry,
.btn-home {
  padding: var(--space-3) var(--space-6);
  border-radius: var(--border-radius-md);
  border: none;
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn-retry {
  background-color: var(--color-primary-600);
  color: #fff;
}

.btn-retry:hover {
  background-color: var(--color-primary-700);
}

.btn-home {
  background-color: var(--color-gray-200);
  color: var(--color-text);
}

.btn-home:hover {
  background-color: var(--color-gray-300);
}
</style>
