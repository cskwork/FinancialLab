<!--
  EmptySheet: the blank-axes sketch used wherever a sheet has nothing on it yet.
-->
<template>
  <div class="empty-sheet" :class="{ compact }">
    <img
      v-if="!compact"
      src="/images/empty-axes.webp"
      width="280"
      height="280"
      alt=""
      loading="lazy"
      decoding="async"
    />
    <div class="empty-body">
      <p class="empty-message"><slot /></p>
      <div v-if="$slots.action" class="empty-action"><slot name="action" /></div>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
</script>

<style scoped>
.empty-sheet {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: var(--space-6);
  align-items: center;
  padding: var(--space-6) 0;
}

.empty-sheet img {
  width: 180px;
  height: 180px;
  mix-blend-mode: multiply;
}

.empty-sheet.compact {
  grid-template-columns: 1fr;
  padding: var(--space-6) 0;
}

.empty-message {
  margin: 0;
  font-size: var(--text-lg);
  color: var(--ink-2);
}

.empty-action {
  margin-top: var(--space-4);
}

@media (prefers-color-scheme: dark) {
  .empty-sheet img {
    /* blueprint: paper drops out, graphite becomes white linework */
    filter: grayscale(1) invert(1) contrast(1.4);
    mix-blend-mode: screen;
  }
}

@media (max-width: 560px) {
  .empty-sheet {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .empty-sheet img {
    width: 140px;
    height: 140px;
  }
}
</style>
