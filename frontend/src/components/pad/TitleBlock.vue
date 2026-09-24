<!--
  TitleBlock: the ruled title block printed at the top of every pad sheet.
  The page H1 lives inside the SUBJECT cell; sheet number is literal wayfinding.
-->
<template>
  <header class="title-block">
    <div class="tb-cell tb-subject">
      <h1 class="tb-title"><slot name="title">{{ title }}</slot></h1>
      <p v-if="$slots.default || subtitle" class="tb-sub"><slot>{{ subtitle }}</slot></p>
    </div>
    <div v-if="sheet" class="tb-cell tb-sheet">
      <span class="tb-key">{{ sheetLabel }}</span>
      <span class="tb-sheet-no fig">{{ sheet }}</span>
    </div>
    <div v-if="$slots.actions" class="tb-cell tb-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
    sheet?: string
    sheetLabel?: string
  }>(),
  {
    title: '',
    subtitle: '',
    sheet: '',
    sheetLabel: 'Sheet'
  }
)
</script>

<style scoped>
.title-block {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  border: 1.5px solid var(--rule-strong);
  margin-bottom: var(--space-10);
  background-color: color-mix(in srgb, var(--paper) 80%, transparent);
}

.tb-cell {
  padding: var(--space-3) var(--space-5) var(--space-4);
  min-width: 0;
}

.tb-cell + .tb-cell {
  border-left: 1.5px solid var(--rule-strong);
}

.tb-key {
  display: block;
  font-family: var(--font-label);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-label);
  text-transform: uppercase;
  color: var(--ink-3);
  margin-bottom: var(--space-1);
}

.tb-title {
  font-size: var(--text-3xl);
  line-height: 1;
}

.tb-sub {
  margin: var(--space-2) 0 0;
  color: var(--ink-2);
  font-size: var(--text-sm);
}

.tb-sheet {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-width: 7rem;
}

.tb-sheet-no {
  font-size: var(--text-xl);
  font-weight: 600;
  white-space: nowrap;
}

.tb-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

@media (max-width: 720px) {
  .title-block {
    grid-template-columns: minmax(0, 1fr) auto;
    margin-bottom: var(--space-8);
  }

  .tb-cell {
    padding: var(--space-3) var(--space-4);
  }

  .tb-actions {
    grid-column: 1 / -1;
    border-left: 0 !important;
    border-top: 1.5px solid var(--rule-strong);
  }

  .tb-sheet {
    min-width: 0;
  }
}
</style>
