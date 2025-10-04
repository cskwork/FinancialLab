<template>
  <div class="loading-spinner" :class="{ 'spinner-fullscreen': fullscreen }">
    <div class="spinner"></div>
    <p v-if="message" class="spinner-message">{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
interface Props {
  message?: string
  fullscreen?: boolean
}

withDefaults(defineProps<Props>(), {
  message: '',
  fullscreen: false
})
</script>

<style scoped>
.loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  padding: var(--space-8);
}

.spinner-fullscreen {
  min-height: 50vh;
}

.spinner {
  width: 48px;
  height: 48px;
  border: 4px solid var(--color-gray-200);
  border-top-color: var(--color-primary-600);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.spinner-message {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  margin: 0;
}
</style>
