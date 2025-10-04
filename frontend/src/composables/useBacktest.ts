/**
 * Backtest Composable
 * 백테스팅 Worker와의 통신을 캡슐화하여 재사용 가능한 형태로 제공
 */

import { ref, computed } from 'vue'
import Decimal from 'decimal.js'

// ========== Types ==========

export interface BacktestParams {
  symbol: string
  startDate: string
  endDate: string
  initialCapital: number
  dcaAmount: number
  dcaInterval: 'WEEKLY' | 'MONTHLY'
  candles: CandleData[]
}

export interface CandleData {
  date: string
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export interface BacktestResult {
  dcaStrategy: StrategyResult
  lumpSumStrategy: StrategyResult
  comparison: {
    dcaFinalValue: number
    lumpSumFinalValue: number
    dcaDifference: number
    lumpSumWins: boolean
  }
}

export interface StrategyResult {
  totalInvested: number
  totalShares: number
  avgCost: number
  finalValue: number
  totalReturn: number
  returnPercentage: number
  trades: TradeRecord[]
}

export interface TradeRecord {
  date: string
  shares: number
  price: number
  cost: number
}

interface WorkerRequest {
  type: 'runBacktest'
  params: BacktestParams
}

interface WorkerResponse {
  type: 'success' | 'error' | 'progress'
  data?: BacktestResult
  error?: string
  progress?: number
}

// ========== Composable ==========

export function useBacktest() {
  // State
  const isRunning = ref(false)
  const progress = ref(0)
  const result = ref<BacktestResult | null>(null)
  const error = ref<string | null>(null)

  let currentWorker: Worker | null = null

  // Computed
  const hasResult = computed(() => result.value !== null)
  const hasError = computed(() => error.value !== null)

  /**
   * DCA 백테스트 실행
   */
  async function runDCABacktest(params: BacktestParams): Promise<BacktestResult | null> {
    // 초기화
    isRunning.value = true
    progress.value = 0
    result.value = null
    error.value = null

    return new Promise((resolve, reject) => {
      try {
        // Worker 생성
        currentWorker = new Worker(new URL('../workers/backtestWorker.ts', import.meta.url), {
          type: 'module'
        })

        // Worker 메시지 핸들러
        currentWorker.onmessage = (event: MessageEvent<WorkerResponse>) => {
          const { type, data, error: workerError, progress: workerProgress } = event.data

          if (type === 'progress' && workerProgress !== undefined) {
            progress.value = workerProgress
          } else if (type === 'success' && data) {
            result.value = data
            isRunning.value = false
            progress.value = 100
            cleanup()
            resolve(data)
          } else if (type === 'error') {
            error.value = workerError ?? '백테스트 실행 중 오류가 발생했습니다'
            isRunning.value = false
            cleanup()
            reject(new Error(error.value))
          }
        }

        // Worker 에러 핸들러
        currentWorker.onerror = (err) => {
          error.value = err.message || 'Worker 실행 중 오류가 발생했습니다'
          isRunning.value = false
          cleanup()
          reject(err)
        }

        // 백테스트 시작
        const request: WorkerRequest = {
          type: 'runBacktest',
          params
        }
        currentWorker.postMessage(request)
      } catch (err) {
        error.value = err instanceof Error ? err.message : '알 수 없는 오류가 발생했습니다'
        isRunning.value = false
        reject(err)
      }
    })
  }

  /**
   * 백테스트 취소
   */
  function cancelBacktest(): void {
    if (currentWorker) {
      currentWorker.terminate()
      cleanup()
    }
    isRunning.value = false
    error.value = '백테스트가 취소되었습니다'
  }

  /**
   * Worker 정리
   */
  function cleanup(): void {
    if (currentWorker) {
      currentWorker.terminate()
      currentWorker = null
    }
  }

  /**
   * 에러 초기화
   */
  function clearError(): void {
    error.value = null
  }

  /**
   * 결과 초기화
   */
  function clearResult(): void {
    result.value = null
    error.value = null
    progress.value = 0
  }

  // ========== Formatting Helpers ==========

  /**
   * 백테스트 결과를 포맷팅하여 UI 표시용으로 변환
   */
  function formatResult(res: BacktestResult) {
    const formatCurrency = (value: number) => {
      return new Decimal(value).toFixed(2)
    }

    const formatPercentage = (value: number) => {
      return new Decimal(value).toFixed(2)
    }

    return {
      dca: {
        invested: formatCurrency(res.dcaStrategy.totalInvested),
        finalValue: formatCurrency(res.dcaStrategy.finalValue),
        totalReturn: formatCurrency(res.dcaStrategy.totalReturn),
        returnPct: formatPercentage(res.dcaStrategy.returnPercentage),
        shares: new Decimal(res.dcaStrategy.totalShares).toFixed(4),
        avgCost: formatCurrency(res.dcaStrategy.avgCost),
        tradeCount: res.dcaStrategy.trades.length
      },
      lumpSum: {
        invested: formatCurrency(res.lumpSumStrategy.totalInvested),
        finalValue: formatCurrency(res.lumpSumStrategy.finalValue),
        totalReturn: formatCurrency(res.lumpSumStrategy.totalReturn),
        returnPct: formatPercentage(res.lumpSumStrategy.returnPercentage),
        shares: new Decimal(res.lumpSumStrategy.totalShares).toFixed(4),
        avgCost: formatCurrency(res.lumpSumStrategy.avgCost),
        tradeCount: res.lumpSumStrategy.trades.length
      },
      comparison: {
        dcaFinalValue: formatCurrency(res.comparison.dcaFinalValue),
        lumpSumFinalValue: formatCurrency(res.comparison.lumpSumFinalValue),
        difference: formatCurrency(Math.abs(res.comparison.dcaDifference)),
        winner: res.comparison.lumpSumWins ? 'Lump Sum' : 'DCA',
        dcaWins: !res.comparison.lumpSumWins
      }
    }
  }

  /**
   * 간단한 백테스트 요약 생성
   */
  function getSummary(res: BacktestResult | null) {
    if (!res) return null

    const winner = res.comparison.lumpSumWins ? 'Lump Sum' : 'DCA'
    const winnerValue = res.comparison.lumpSumWins
      ? res.comparison.lumpSumFinalValue
      : res.comparison.dcaFinalValue
    const loserValue = res.comparison.lumpSumWins
      ? res.comparison.dcaFinalValue
      : res.comparison.lumpSumFinalValue

    const advantage = ((winnerValue - loserValue) / loserValue) * 100

    return {
      winner,
      advantage: new Decimal(advantage).toFixed(2),
      dcaReturn: new Decimal(res.dcaStrategy.returnPercentage).toFixed(2),
      lumpSumReturn: new Decimal(res.lumpSumStrategy.returnPercentage).toFixed(2)
    }
  }

  // ========== Return API ==========

  return {
    // State
    isRunning,
    progress,
    result,
    error,

    // Computed
    hasResult,
    hasError,

    // Actions
    runDCABacktest,
    cancelBacktest,
    clearError,
    clearResult,

    // Helpers
    formatResult,
    getSummary
  }
}
