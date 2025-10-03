/**
 * Config Store
 * 백엔드 모드 및 연결 정보 관리
 */

import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

const STORAGE_KEYS = {
  backendMode: 'finlab_backend_mode',
  backendUrl: 'finlab_backend_url'
} as const

export type BackendMode = 'mock' | 'live'

export const useConfigStore = defineStore('config', () => {
  const backendMode = ref<BackendMode>('mock')
  const backendUrl = ref<string>('https://finlab-api.fly.dev')

  const isMockMode = computed(() => backendMode.value === 'mock')
  const isLiveMode = computed(() => backendMode.value === 'live')

  function setBackendMode(mode: BackendMode) {
    backendMode.value = mode
  }

  function setBackendUrl(url: string) {
    backendUrl.value = url
  }

  function restore() {
    const storedMode = localStorage.getItem(STORAGE_KEYS.backendMode) as BackendMode | null
    const storedUrl = localStorage.getItem(STORAGE_KEYS.backendUrl)

    if (storedMode === 'mock' || storedMode === 'live') {
      backendMode.value = storedMode
    }

    if (storedUrl) {
      backendUrl.value = storedUrl
    }
  }

  watch(backendMode, (mode) => {
    localStorage.setItem(STORAGE_KEYS.backendMode, mode)
  })

  watch(backendUrl, (url) => {
    localStorage.setItem(STORAGE_KEYS.backendUrl, url)
  })

  return {
    backendMode,
    backendUrl,
    isMockMode,
    isLiveMode,
    setBackendMode,
    setBackendUrl,
    restore
  }
})
