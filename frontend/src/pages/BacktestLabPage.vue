<template>
  <div class="backtest-page">
    <header class="page-header">
      <h1>Backtest Lab</h1>
      <p class="subtitle">Compare dollar-cost averaging against lump-sum investing using local OHLCV data.</p>
    </header>

    <section class="form-section">
      <form class="form-grid" @submit.prevent="runBacktest">
        <label>
          Symbol
          <select v-model="form.symbol" @change="handleSymbolChange" required>
            <option v-for="symbol in symbols" :key="symbol" :value="symbol">{{ symbol }}</option>
          </select>
        </label>
        <label>
          Start Date
          <input v-model="form.startDate" type="date" required />
        </label>
        <label>
          End Date
          <input v-model="form.endDate" type="date" required />
        </label>
        <label>
          Initial Capital (USD)
          <input v-model.number="form.initialCapital" type="number" min="100" step="100" required />
        </label>
        <label>
          DCA Amount (USD)
          <input v-model.number="form.dcaAmount" type="number" min="1" step="50" required />
        </label>
        <label>
          Interval
          <select v-model="form.dcaInterval" required>
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
          </select>
        </label>
        <div class="form-actions">
          <button class="btn-primary" type="submit" :disabled="isRunning || !canRun">
            {{ isRunning ? 'Running...' : 'Run Backtest' }}
          </button>
        </div>
      </form>
    </section>

    <section v-if="result" class="results-section">
      <div class="result-card">
        <h2>Results</h2>
        <div class="results-grid">
          <div class="result-group">
            <h3>DCA Strategy</h3>
            <dl>
              <div>
                <dt>Total Invested</dt>
                <dd>${{ formatCurrency(result.dcaStrategy.totalInvested) }}</dd>
              </div>
              <div>
                <dt>Final Value</dt>
                <dd>${{ formatCurrency(result.dcaStrategy.finalValue) }}</dd>
              </div>
              <div>
                <dt>Return %</dt>
                <dd>{{ result.dcaStrategy.returnPercentage.toFixed(2) }}%</dd>
              </div>
            </dl>
          </div>
          <div class="result-group">
            <h3>Lump Sum</h3>
            <dl>
              <div>
                <dt>Total Invested</dt>
                <dd>${{ formatCurrency(result.lumpSumStrategy.totalInvested) }}</dd>
              </div>
              <div>
                <dt>Final Value</dt>
                <dd>${{ formatCurrency(result.lumpSumStrategy.finalValue) }}</dd>
              </div>
              <div>
                <dt>Return %</dt>
                <dd>{{ result.lumpSumStrategy.returnPercentage.toFixed(2) }}%</dd>
              </div>
            </dl>
          </div>
        </div>
        <div class="comparison">
          <p>
            <strong>{{ result.comparison.lumpSumWins ? 'Lump Sum outperformed.' : 'DCA outperformed.' }}</strong>
            Difference: ${{ formatCurrency(Math.abs(result.comparison.dcaDifference)) }}
          </p>
        </div>
      </div>
    </section>

    <p v-if="!result && !isRunning" class="hint">Run the simulation to see which approach wins for your chosen symbol and period.</p>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { db } from '@/services/db/schema'
import { useToast } from '@/composables/useToast'

interface BacktestForm {
  symbol: string
  startDate: string
  endDate: string
  initialCapital: number
  dcaAmount: number
  dcaInterval: 'WEEKLY' | 'MONTHLY'
}

interface BacktestResult {
  dcaStrategy: {
    totalInvested: number
    finalValue: number
    returnPercentage: number
  }
  lumpSumStrategy: {
    totalInvested: number
    finalValue: number
    returnPercentage: number
  }
  comparison: {
    dcaFinalValue: number
    lumpSumFinalValue: number
    dcaDifference: number
    lumpSumWins: boolean
  }
}

const toast = useToast()
const symbols = ref<string[]>([])
const isRunning = ref(false)
const result = ref<BacktestResult | null>(null)
const form = reactive<BacktestForm>({
  symbol: '',
  startDate: '',
  endDate: '',
  initialCapital: 10000,
  dcaAmount: 500,
  dcaInterval: 'MONTHLY'
})

const canRun = computed(() => {
  return (
    form.symbol !== '' &&
    form.startDate !== '' &&
    form.endDate !== '' &&
    new Date(form.startDate) < new Date(form.endDate)
  )
})

async function loadSymbols() {
  const uniqueSymbols = new Set<string>()
  await db.candles.orderBy('symbol').each((candle) => {
    uniqueSymbols.add(candle.symbol)
  })

  symbols.value = Array.from(uniqueSymbols)
  if (symbols.value.length > 0) {
    form.symbol = symbols.value[0]!
    await updateDateRange()
  }
}

async function updateDateRange() {
  if (!form.symbol) return
  const candles = await db.candles.where('symbol').equals(form.symbol).sortBy('date')
  if (candles.length === 0) return

  form.startDate = candles[0]!.date
  form.endDate = candles[candles.length - 1]!.date
}

function handleSymbolChange() {
  updateDateRange()
  result.value = null
}

function formatCurrency(value: number): string {
  return value.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

async function runBacktest() {
  if (!canRun.value) {
    toast.warning('유효한 기간과 금액을 입력하세요.')
    return
  }

  try {
    isRunning.value = true
    result.value = null

    const candles = await db.candles
      .where('symbol')
      .equals(form.symbol)
      .filter((candle) => candle.date >= form.startDate && candle.date <= form.endDate)
      .toArray()

    if (candles.length < 10) {
      toast.warning('선택한 구간의 데이터가 부족합니다.')
      return
    }

    const worker = new Worker(new URL('../workers/backtestWorker.ts', import.meta.url), {
      type: 'module'
    })

    result.value = await new Promise<BacktestResult>((resolve, reject) => {
      worker.onmessage = (event: MessageEvent<any>) => {
        if (event.data.type === 'success') {
          resolve(event.data.data)
        } else if (event.data.type === 'error') {
          reject(new Error(event.data.error ?? 'Backtest worker failed'))
        }
        worker.terminate()
      }

      worker.onerror = (error) => {
        worker.terminate()
        reject(error)
      }

      worker.postMessage({
        type: 'runBacktest',
        params: {
          symbol: form.symbol,
          startDate: form.startDate,
          endDate: form.endDate,
          initialCapital: form.initialCapital,
          dcaAmount: form.dcaAmount,
          dcaInterval: form.dcaInterval,
          candles: candles.map((candle) => ({
            date: candle.date,
            open: candle.open,
            high: candle.high,
            low: candle.low,
            close: candle.close,
            volume: candle.volume
          }))
        }
      })
    })

    toast.success('백테스트가 완료되었습니다.')
  } catch (error) {
    console.error(error)
    toast.error(error instanceof Error ? error.message : '백테스트 실행에 실패했습니다.')
  } finally {
    isRunning.value = false
  }
}

onMounted(() => {
  loadSymbols()
})
</script>

<style scoped>
.backtest-page {
  min-height: 100vh;
  padding: var(--space-6);
  background: linear-gradient(180deg, rgba(37, 99, 235, 0.05) 0%, rgba(255, 255, 255, 1) 60%);
}

.page-header {
  max-width: 960px;
  margin: 0 auto var(--space-6);
}

.page-header h1 {
  margin: 0;
  font-size: var(--font-size-3xl);
}

.subtitle {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  max-width: 640px;
}

.form-section {
  max-width: 960px;
  margin: 0 auto var(--space-6);
  background-color: var(--color-background);
  padding: var(--space-6);
  border-radius: var(--border-radius-2xl);
  box-shadow: var(--shadow-md);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-4);
}

.form-grid label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
}

.form-grid input,
.form-grid select {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
}

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
}

.btn-primary {
  padding: var(--space-3) var(--space-6);
  background-color: var(--color-primary-600);
  color: #fff;
  border: none;
  border-radius: var(--border-radius-md);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn-primary:disabled {
  background-color: var(--color-gray-400);
  cursor: not-allowed;
}

.btn-primary:not(:disabled):hover {
  background-color: var(--color-primary-700);
}

.results-section {
  max-width: 960px;
  margin: 0 auto;
}

.result-card {
  background-color: var(--color-background);
  border-radius: var(--border-radius-2xl);
  box-shadow: var(--shadow-md);
  padding: var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.results-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--space-4);
}

.result-group h3 {
  margin: 0 0 var(--space-3);
}

.result-group dl {
  display: grid;
  gap: var(--space-2);
}

.result-group dt {
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.result-group dd {
  margin: 0;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.comparison {
  padding-top: var(--space-3);
  border-top: 1px solid var(--color-border);
}

.hint {
  text-align: center;
  margin-top: var(--space-6);
  color: var(--color-text-muted);
}

@media (max-width: 768px) {
  .form-actions {
    justify-content: center;
  }
}
</style>
