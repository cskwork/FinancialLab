<template>
  <span ref="wrapperRef" class="help-tooltip-wrapper">
    <button
      type="button"
      class="help-trigger-btn"
      :aria-label="label || term || t('common.help')"
      :aria-expanded="isOpen"
      @click.stop="toggle"
      @keydown.escape="close"
    >
      <HelpCircle class="help-icon" aria-hidden="true" />
      <span class="sr-only">{{ label || term || t('common.help') }}</span>
    </button>

    <transition name="popover-fade">
      <div
        v-if="isOpen"
        class="help-popover"
        role="tooltip"
        @click.stop
      >
        <div class="popover-head">
          <strong v-if="term" class="popover-term">{{ term }}</strong>
          <button
            type="button"
            class="popover-close-btn"
            :aria-label="t('common.close')"
            @click.stop="close"
          >
            <X class="close-icon" aria-hidden="true" />
          </button>
        </div>
        <p class="popover-body">{{ text }}</p>
        <p v-if="tip" class="popover-tip">
          <span class="tip-badge">TIP</span> {{ tip }}
        </p>
      </div>
    </transition>
  </span>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { HelpCircle, X } from 'lucide-vue-next'

defineProps<{
  text: string
  term?: string
  tip?: string
  label?: string
}>()

const { t } = useI18n()
const isOpen = ref(false)
const wrapperRef = ref<HTMLElement | null>(null)

function toggle() {
  isOpen.value = !isOpen.value
}

function close() {
  isOpen.value = false
}

function handleDocumentClick(event: MouseEvent) {
  if (wrapperRef.value && !wrapperRef.value.contains(event.target as Node)) {
    close()
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>

<style scoped>
.help-tooltip-wrapper {
  position: relative;
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
  margin-left: 0.35rem;
  line-height: 1;
}

.help-trigger-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.15rem;
  height: 1.15rem;
  padding: 0;
  border-radius: 50%;
  border: 1px solid var(--ink-3);
  background: var(--paper-raised);
  color: var(--ink-2);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out);
}

.help-trigger-btn:hover,
.help-trigger-btn:focus-visible {
  color: var(--ink);
  border-color: var(--ink);
  background: var(--highlight);
  outline: none;
}

.help-icon {
  width: 0.75rem;
  height: 0.75rem;
}

.help-popover {
  position: absolute;
  top: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  width: max-content;
  max-width: 280px;
  min-width: 200px;
  padding: var(--space-3) var(--space-4);
  background-color: var(--paper-raised);
  border: 1.5px solid var(--ink);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  text-align: left;
  white-space: normal;
}

@media (max-width: 600px) {
  .help-popover {
    left: auto;
    right: -10px;
    transform: none;
    max-width: 240px;
  }
}

.popover-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  padding-bottom: var(--space-1);
  border-bottom: 1px solid var(--rule);
}

.popover-term {
  font-family: var(--font-label);
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--ink);
  letter-spacing: 0.02em;
}

.popover-close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
  color: var(--ink-3);
  cursor: pointer;
}

.popover-close-btn:hover {
  color: var(--ink);
}

.close-icon {
  width: 14px;
  height: 14px;
}

.popover-body {
  margin: 0;
  font-family: var(--font-body);
  font-size: var(--text-xs);
  line-height: 1.5;
  color: var(--ink);
}

.popover-tip {
  margin: var(--space-2) 0 0;
  padding-top: var(--space-2);
  border-top: 1px dashed var(--rule);
  font-family: var(--font-body);
  font-size: var(--text-xs);
  color: var(--ink-2);
  line-height: 1.45;
}

.tip-badge {
  display: inline-block;
  padding: 1px 4px;
  background-color: var(--highlight);
  color: var(--highlight-ink);
  font-family: var(--font-label);
  font-weight: 700;
  font-size: 0.65rem;
  letter-spacing: 0.05em;
  margin-right: 2px;
}

.popover-fade-enter-active,
.popover-fade-leave-active {
  transition: opacity var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.popover-fade-enter-from,
.popover-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-4px);
}
</style>
