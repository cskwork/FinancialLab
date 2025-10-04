/**
 * Portfolio Composable
 * 포트폴리오 관련 로직을 캡슐화하여 재사용 가능한 형태로 제공
 */

import { computed } from 'vue'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { useAuthStore } from '@/stores/authStore'

export function usePortfolio() {
  const portfolioStore = usePortfolioStore()
  const authStore = useAuthStore()

  // ========== State (from store) ==========

  const portfolios = computed(() => portfolioStore.portfolios)
  const currentPortfolio = computed(() => portfolioStore.currentPortfolio)
  const currentPortfolioId = computed(() => portfolioStore.currentPortfolioId)
  const isLoading = computed(() => portfolioStore.isLoading)
  const error = computed(() => portfolioStore.error)

  // ========== Computed Helpers ==========

  const hasPortfolios = computed(() => portfolioStore.hasPortfolios)
  const portfolioCount = computed(() => portfolioStore.portfolios.length)

  const isAuthenticated = computed(() => authStore.isAuthenticated)

  /**
   * 현재 선택된 포트폴리오 요약 정보
   */
  const currentPortfolioSummary = computed(() => {
    if (!currentPortfolio.value) return null

    return {
      id: currentPortfolio.value.id,
      name: currentPortfolio.value.name,
      currency: currentPortfolio.value.baseCurrency,
      createdAt: currentPortfolio.value.createdAt,
      updatedAt: currentPortfolio.value.updatedAt
    }
  })

  /**
   * 포트폴리오 선택 옵션 (드롭다운용)
   */
  const portfolioOptions = computed(() => {
    return portfolios.value.map((p) => ({
      value: p.id!,
      label: `${p.name} (${p.baseCurrency})`,
      portfolio: p
    }))
  })

  // ========== Actions ==========

  /**
   * 포트폴리오 목록 조회
   */
  async function fetchPortfolios(): Promise<void> {
    await portfolioStore.fetchPortfolios()
  }

  /**
   * 포트폴리오 생성
   */
  async function createPortfolio(name: string, baseCurrency: string): Promise<boolean> {
    return await portfolioStore.createPortfolio(name, baseCurrency)
  }

  /**
   * 포트폴리오 업데이트
   */
  async function updatePortfolio(
    id: number,
    updates: { name?: string; baseCurrency?: string }
  ): Promise<boolean> {
    return await portfolioStore.updatePortfolio(id, updates)
  }

  /**
   * 포트폴리오 삭제
   */
  async function deletePortfolio(id: number): Promise<boolean> {
    return await portfolioStore.deletePortfolio(id)
  }

  /**
   * 포트폴리오 선택
   */
  function selectPortfolio(id: number): void {
    const portfolio = findPortfolioById(id)
    if (portfolio) {
      portfolioStore.setCurrentPortfolio(portfolio)
    }
  }

  /**
   * 특정 ID로 포트폴리오 찾기
   */
  function findPortfolioById(id: number) {
    return portfolios.value.find((p) => p.id === id)
  }

  /**
   * 에러 초기화
   */
  function clearError(): void {
    portfolioStore.error = null
  }

  /**
   * 데모 포트폴리오 생성 (개발/테스트용)
   */
  async function createDemoPortfolio(): Promise<boolean> {
    return await createPortfolio('Demo Portfolio', 'USD')
  }

  // ========== Return API ==========

  return {
    // State
    portfolios,
    currentPortfolio,
    currentPortfolioId,
    isLoading,
    error,
    isAuthenticated,

    // Computed
    hasPortfolios,
    portfolioCount,
    currentPortfolioSummary,
    portfolioOptions,

    // Actions
    fetchPortfolios,
    createPortfolio,
    updatePortfolio,
    deletePortfolio,
    selectPortfolio,
    findPortfolioById,
    clearError,
    createDemoPortfolio
  }
}
