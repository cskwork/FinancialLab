<template>
  <div>
    <slot v-if="!hasError" />
    <div v-else class="error-fallback" role="alert">
      <h1>{{ t('errorBoundary.title') }}</h1>
      <p class="message">{{ errorMessage ?? t('errorBoundary.unknown') }}</p>
      <div class="actions">
        <button type="button" class="btn btn--primary" @click="handleRetry">{{ t('errorBoundary.retry') }}</button>
        <button type="button" class="btn" @click="goHome">{{ t('errorBoundary.goDashboard') }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useToast } from '@/composables/useToast'

const hasError = ref(false)
// null이면 현재 언어의 기본 문구를 보여준다
const errorMessage = ref<string | null>(null)
const router = useRouter()
const { t } = useI18n()
const toast = useToast()

function resetError() {
  hasError.value = false
  errorMessage.value = null
}

function handleRetry() {
  resetError()
  toast.info(t('errorBoundary.reloaded'))
}

function goHome() {
  resetError()
  router.push('/dashboard')
}

onErrorCaptured((err) => {
  hasError.value = true

  // 브라우저 확장 프로그램 충돌 감지
  if (err instanceof Error && (
    err.message.includes('Identifier') && err.message.includes('already been declared') ||
    err.message.includes('Invalid linked format') ||
    err.message.includes('contentscript')
  )) {
    errorMessage.value = t('errorBoundary.extensionConflict')
    toast.error(t('errorBoundary.extensionToast'))
  } else {
    errorMessage.value = err instanceof Error ? err.message : t('errorBoundary.unexpected')
    toast.error(t('errorBoundary.genericToast'))
  }

  console.error('[ErrorBoundary]', err)
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
  color: var(--red-pencil);
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
</style>
