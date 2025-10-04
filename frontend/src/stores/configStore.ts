/**
 * Config Store
 * 백엔드 모드 및 연결 정보 관리
 */

import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

const STORAGE_KEYS = {
  backendMode: 'finlab_backend_mode',
  backendUrl: 'finlab_backend_url',
  locale: 'finlab_locale'
} as const

export type BackendMode = 'mock' | 'live'
export type Locale = 'ko' | 'en'

export const useConfigStore = defineStore('config', () => {
  const backendMode = ref<BackendMode>('mock')
  const backendUrl = ref<string>('https://finlab-api.fly.dev')
  const locale = ref<Locale>('ko') // 기본 언어: 한국어

  const isMockMode = computed(() => backendMode.value === 'mock')
  const isLiveMode = computed(() => backendMode.value === 'live')

  function setBackendMode(mode: BackendMode) {
    backendMode.value = mode
  }

  function setBackendUrl(url: string) {
    backendUrl.value = url
  }

  function setLocale(newLocale: Locale) {
    locale.value = newLocale
  }

  function restore() {
    const storedMode = localStorage.getItem(STORAGE_KEYS.backendMode) as BackendMode | null
    const storedUrl = localStorage.getItem(STORAGE_KEYS.backendUrl)
    const storedLocale = localStorage.getItem(STORAGE_KEYS.locale) as Locale | null

    if (storedMode === 'mock' || storedMode === 'live') {
      backendMode.value = storedMode
    }

    if (storedUrl) {
      backendUrl.value = storedUrl
    }

    if (storedLocale === 'ko' || storedLocale === 'en') {
      locale.value = storedLocale
    }
  }

  watch(backendMode, (mode) => {
    localStorage.setItem(STORAGE_KEYS.backendMode, mode)
  })

  watch(backendUrl, (url) => {
    localStorage.setItem(STORAGE_KEYS.backendUrl, url)
  })

  watch(locale, (newLocale) => {
    localStorage.setItem(STORAGE_KEYS.locale, newLocale)
  })

  return {
    backendMode,
    backendUrl,
    locale,
    isMockMode,
    isLiveMode,
    setBackendMode,
    setBackendUrl,
    setLocale,
    restore
  }
})
