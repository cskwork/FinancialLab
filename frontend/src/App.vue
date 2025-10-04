<template>
  <div id="app">
    <NavBar v-if="shouldShowNav" />
    <ErrorBoundary>
      <RouterView />
    </ErrorBoundary>
    <ToastContainer />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import NavBar from './components/common/NavBar.vue'
import ErrorBoundary from './components/layout/ErrorBoundary.vue'
import ToastContainer from './components/layout/ToastContainer.vue'

const route = useRoute()

// Landing 페이지와 Login 페이지에서는 NavBar 숨김
const shouldShowNav = computed(() => {
  return route.name !== 'landing' && route.name !== 'login'
})
</script>

<style>
#app {
  min-height: 100vh;
}
</style>
