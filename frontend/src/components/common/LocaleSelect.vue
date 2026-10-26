<!--
  LocaleSelect: native language select bound to configStore (persisted as finlab_locale).
  Used inside the nav settings panel and, compactly, on the landing and login sheets where the nav is hidden.
-->
<template>
  <div class="field locale-select" :class="{ compact }">
    <label :for="id" :class="{ 'sr-only': compact }">{{ compact ? t('common.languageSelector') : t('nav.language') }}</label>
    <div class="control">
      <Languages v-if="compact" class="icon" aria-hidden="true" />
      <select :id="id" v-model="currentLocale">
        <option value="en" lang="en">{{ t('nav.english') }}</option>
        <option value="ko" lang="ko">{{ t('nav.korean') }}</option>
      </select>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Languages } from 'lucide-vue-next'
import { useConfigStore, type Locale } from '@/stores/configStore'

withDefaults(defineProps<{ id?: string; compact?: boolean }>(), {
  id: 'language-select',
  compact: false
})

const { t, locale } = useI18n()
const configStore = useConfigStore()

const currentLocale = computed({
  get: () => configStore.locale,
  set: (value: Locale) => {
    configStore.setLocale(value)
    locale.value = value
  }
})
</script>

<style scoped>
.control {
  display: flex;
  align-items: center;
}

.compact .control {
  position: relative;
}

.compact .icon {
  position: absolute;
  left: var(--space-2);
  width: 16px;
  height: 16px;
  pointer-events: none;
  color: var(--ink-2);
}

.compact select {
  min-height: 44px;
  width: auto;
  padding-left: calc(var(--space-2) + 22px);
}
</style>
