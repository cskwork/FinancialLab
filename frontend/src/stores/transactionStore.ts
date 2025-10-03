/**
 * Transaction Store
 * 거래 내역 상태 관리 (Pinia)
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiClient } from '@/services/api'
import type { TransactionResponse, CreateTransactionRequest } from '@/services/api/schemas'
import type { Transaction } from '@/services/db/schema'
import { calculateHoldingsFromTransactions } from '@/utils/fifoCalculator'
import type { Holding } from '@/utils/fifoCalculator'
import { useAuthStore } from './authStore'

export const useTransactionStore = defineStore('transaction', () => {
  const authStore = useAuthStore()

  // State
  const transactions = ref<TransactionResponse[]>([])
  const holdings = ref<Map<string, Holding>>(new Map())
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // Getters
  const sortedTransactions = computed(() => {
    return [...transactions.value].sort(
      (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    )
  })

  const buyTransactions = computed(() => {
    return transactions.value.filter((t) => t.type === 'BUY')
  })

  const sellTransactions = computed(() => {
    return transactions.value.filter((t) => t.type === 'SELL')
  })

  const holdingsArray = computed(() => {
    return Array.from(holdings.value.values())
  })

  // Actions

  /**
   * 거래 목록 조회
   */
  async function fetchTransactions(portfolioId: number) {
    isLoading.value = true
    error.value = null

    try {
      const response = await apiClient.getTransactions(portfolioId, authStore.token)

      if (response.success && response.data) {
        transactions.value = response.data

        // FIFO 계산
        recalculateHoldings()
      } else {
        error.value = response.error?.message ?? '거래 내역 조회에 실패했습니다'
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '알 수 없는 오류가 발생했습니다'
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 거래 생성
   */
  async function createTransaction(request: CreateTransactionRequest): Promise<boolean> {
    isLoading.value = true
    error.value = null

    try {
      const response = await apiClient.createTransaction(request, authStore.token)

      if (response.success && response.data) {
        transactions.value.push(response.data)

        // FIFO 재계산
        recalculateHoldings()

        return true
      } else {
        error.value = response.error?.message ?? '거래 생성에 실패했습니다'
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
   * 거래 삭제
   */
  async function deleteTransaction(id: number): Promise<boolean> {
    isLoading.value = true
    error.value = null

    try {
      const response = await apiClient.deleteTransaction(id, authStore.token)

      if (response.success) {
        transactions.value = transactions.value.filter((t) => t.id !== id)

        // FIFO 재계산
        recalculateHoldings()

        return true
      } else {
        error.value = response.error?.message ?? '거래 삭제에 실패했습니다'
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
   * FIFO Holdings 재계산
   */
  function recalculateHoldings() {
    // TransactionResponse를 Transaction으로 변환
    const transactionData: Transaction[] = transactions.value.map((t) => ({
      id: t.id,
      portfolioId: t.portfolioId,
      type: t.type,
      symbol: t.symbol,
      qty: t.qty,
      price: t.price,
      fee: t.fee,
      timestamp: new Date(t.timestamp),
      note: t.note
    }))

    try {
      holdings.value = calculateHoldingsFromTransactions(transactionData)
    } catch (err) {
      console.error('Failed to calculate holdings:', err)
      error.value = 'Holdings 계산에 실패했습니다'
    }
  }

  /**
   * 특정 심볼의 holding 조회
   */
  function getHolding(symbol: string): Holding | undefined {
    return holdings.value.get(symbol)
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
    transactions.value = []
    holdings.value = new Map()
    error.value = null
  }

  return {
    // State
    transactions,
    holdings,
    isLoading,
    error,
    // Getters
    sortedTransactions,
    buyTransactions,
    sellTransactions,
    holdingsArray,
    // Actions
    fetchTransactions,
    createTransaction,
    deleteTransaction,
    recalculateHoldings,
    getHolding,
    clearError,
    reset
  }
})
