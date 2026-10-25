/**
 * Portfolio Store
 * 포트폴리오 상태 관리 (Pinia)
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiClient } from '@/services/api'
import type { PortfolioResponse } from '@/services/api/schemas'
import { useAuthStore } from './authStore'

export const usePortfolioStore = defineStore('portfolio', () => {
  const authStore = useAuthStore()

  // State
  const portfolios = ref<PortfolioResponse[]>([])
  const currentPortfolio = ref<PortfolioResponse | null>(null)
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // Getters
  const currentPortfolioId = computed(() => currentPortfolio.value?.id ?? null)
  const hasPortfolios = computed(() => portfolios.value.length > 0)

  // Actions

  /**
   * 포트폴리오 목록 조회
   */
  async function fetchPortfolios() {
    if (!authStore.currentUserId) {
      error.value = '로그인이 필요합니다'
      return
    }

    isLoading.value = true
    error.value = null

    try {
      const response = await apiClient.getPortfolios(authStore.currentUserId, authStore.token)

      if (response.success && response.data) {
        portfolios.value = response.data

        // 현재 포트폴리오가 없으면 저장된 ID 또는 첫 번째로 설정
        if (response.data.length > 0) {
          let selected: PortfolioResponse | null = null
          try {
            const savedId = localStorage.getItem('selected_portfolio_id')
            if (savedId) {
              selected = response.data.find((p) => p.id === Number(savedId)) ?? null
            }
          } catch {
            // storage unavailable
          }
          currentPortfolio.value = selected ?? currentPortfolio.value ?? response.data[0]!
        }
      } else {
        error.value = response.error?.message ?? '포트폴리오 조회에 실패했습니다'
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '알 수 없는 오류가 발생했습니다'
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 포트폴리오 생성
   */
  async function createPortfolio(name: string, baseCurrency: string): Promise<boolean> {
    if (!authStore.currentUserId) {
      error.value = '로그인이 필요합니다'
      return false
    }

    isLoading.value = true
    error.value = null

    try {
      const response = await apiClient.createPortfolio(authStore.currentUserId, {
        name,
        baseCurrency
      }, authStore.token)

      if (response.success && response.data) {
        portfolios.value.push(response.data)

        // 첫 번째 포트폴리오면 자동 선택
        if (portfolios.value.length === 1) {
          currentPortfolio.value = response.data
        }

        return true
      } else {
        error.value = response.error?.message ?? '포트폴리오 생성에 실패했습니다'
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
   * 포트폴리오 업데이트
   */
  async function updatePortfolio(
    id: number,
    updates: { name?: string; baseCurrency?: string }
  ): Promise<boolean> {
    isLoading.value = true
    error.value = null

    try {
      const response = await apiClient.updatePortfolio(id, updates, authStore.token)

      if (response.success && response.data) {
        const index = portfolios.value.findIndex((p) => p.id === id)
        if (index !== -1) {
          portfolios.value[index] = response.data
        }

        // 현재 포트폴리오 업데이트
        if (currentPortfolio.value?.id === id) {
          currentPortfolio.value = response.data
        }

        return true
      } else {
        error.value = response.error?.message ?? '포트폴리오 수정에 실패했습니다'
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
   * 포트폴리오 삭제
   */
  async function deletePortfolio(id: number): Promise<boolean> {
    isLoading.value = true
    error.value = null

    try {
      const response = await apiClient.deletePortfolio(id, authStore.token)

      if (response.success) {
        portfolios.value = portfolios.value.filter((p) => p.id !== id)

        // 삭제된 포트폴리오가 현재 선택된 것이면 초기화
        if (currentPortfolio.value?.id === id) {
          currentPortfolio.value = portfolios.value[0] ?? null
        }

        return true
      } else {
        error.value = response.error?.message ?? '포트폴리오 삭제에 실패했습니다'
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
   * 현재 포트폴리오 선택
   */
  function setCurrentPortfolio(portfolio: PortfolioResponse) {
    currentPortfolio.value = portfolio
    try {
      localStorage.setItem('selected_portfolio_id', String(portfolio.id))
    } catch {
      // storage unavailable
    }
  }

  /**
   * 에러 클리어
   */
  function clearError() {
    error.value = null
  }

  /**
   * 상태 초기화
   */
  function reset() {
    portfolios.value = []
    currentPortfolio.value = null
    error.value = null
    try {
      localStorage.removeItem('selected_portfolio_id')
    } catch {
      // storage unavailable
    }
  }

  return {
    // State
    portfolios,
    currentPortfolio,
    isLoading,
    error,
    // Getters
    currentPortfolioId,
    hasPortfolios,
    // Actions
    fetchPortfolios,
    createPortfolio,
    updatePortfolio,
    deletePortfolio,
    setCurrentPortfolio,
    clearError,
    reset
  }
})
