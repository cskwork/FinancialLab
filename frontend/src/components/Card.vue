<template>
  <div class="card" :class="{ 'card-hover': hover }">
    <div v-if="$slots.header" class="card-header">
      <slot name="header"></slot>
    </div>
    <div class="card-body">
      <slot></slot>
    </div>
    <div v-if="$slots.footer" class="card-footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  hover?: boolean
}

withDefaults(defineProps<Props>(), {
  hover: false
})
</script>

<style scoped>
.card {
  background-color: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.card-hover {
  transition: all var(--transition-base);
}

.card-hover:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.card-header {
  padding: var(--space-4) var(--space-6);
  border-bottom: 1px solid var(--color-border);
  font-weight: var(--font-weight-medium);
}

.card-body {
  padding: var(--space-6);
}

.card-footer {
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--color-border);
  background-color: var(--color-gray-50);
}
</style>
