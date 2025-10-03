/**
 * Auth Store
 * 사용자 인증 상태 관리 (Pinia)
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiClient } from '@/services/api'
import type { LoginResponse } from '@/services/api/schemas'

export const useAuthStore = defineStore('auth', () => {
  // State
  const user = ref<LoginResponse | null>(null)
  const token = ref<string | null>(null)
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // Getters
  const isAuthenticated = computed(() => !!user.value && !!token.value)
  const currentUserId = computed(() => user.value?.userId ?? null)
  const currentUserEmail = computed(() => user.value?.email ?? null)

  // Actions

  /**
   * 로그인
   */
  async function login(email: string): Promise<boolean> {
    isLoading.value = true
    error.value = null

    try {
      const response = await apiClient.login({ email })

      if (response.success && response.data) {
        user.value = response.data
        token.value = response.data.token

        // localStorage에 토큰 저장
        localStorage.setItem('auth_token', response.data.token)
        localStorage.setItem('user', JSON.stringify(response.data))

        return true
      } else {
        error.value = response.error?.message ?? '로그인에 실패했습니다'
        return false
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '알 수 없는 오류가 발생했습니다'
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 로그아웃
   */
  function logout() {
    user.value = null
    token.value = null
    localStorage.removeItem('auth_token')
    localStorage.removeItem('user')
  }

  /**
   * localStorage에서 세션 복원
   */
  function restoreSession() {
    const savedToken = localStorage.getItem('auth_token')
    const savedUser = localStorage.getItem('user')

    if (savedToken && savedUser) {
      try {
        token.value = savedToken
        user.value = JSON.parse(savedUser)
      } catch (err) {
        console.error('Failed to restore session:', err)
        logout()
      }
    }
  }

  /**
   * 에러 클리어
   */
  function clearError() {
    error.value = null
  }

  return {
    // State
    user,
    token,
    isLoading,
    error,
    // Getters
    isAuthenticated,
    currentUserId,
    currentUserEmail,
    // Actions
    login,
    logout,
    restoreSession,
    clearError
  }
})
