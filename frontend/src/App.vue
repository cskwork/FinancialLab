<template>
  <div id="app">
    <NavBar v-if="shouldShowNav" />
    <main id="main" class="app-main">
      <ErrorBoundary>
        <RouterView />
      </ErrorBoundary>
    </main>
    <footer v-if="shouldShowNav" class="app-foot">
      <span class="label">FinLab</span>
      <span>{{ t('common.disclaimer') }}</span>
    </footer>
    <ToastContainer />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import NavBar from './components/common/NavBar.vue'
import ErrorBoundary from './components/layout/ErrorBoundary.vue'
import ToastContainer from './components/layout/ToastContainer.vue'

const route = useRoute()
const { t } = useI18n()

// Landing 페이지와 Login 페이지에서는 NavBar 숨김
const shouldShowNav = computed(() => {
  return route.name !== 'landing' && route.name !== 'login'
})
</script>

<style>
#app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-main {
  flex: 1;
}

.app-foot {
  max-width: var(--page-max);
  width: 100%;
  margin: 0 auto;
  padding: var(--space-4) var(--space-6) var(--space-8);
  display: flex;
  gap: var(--space-3);
  align-items: baseline;
  flex-wrap: wrap;
  border-top: 1px solid var(--rule);
  color: var(--ink-2);
  font-size: var(--text-sm);
}
</style>
