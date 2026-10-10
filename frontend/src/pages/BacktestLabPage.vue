<template>
  <div class="page backtest-page">
    <TitleBlock :title="t('backtest.title')" sheet="04 / 04">
      {{ t('backtest.subtitle') }}
    </TitleBlock>

    <div class="lab-grid">
      <section class="block given" aria-labelledby="given-heading">
        <div class="block-head">
          <h2 id="given-heading">{{ t('backtest.given') }}</h2>
        </div>
        <form class="form-grid" novalidate @submit.prevent="runBacktest">
          <div class="field">
            <label for="bt-symbol">{{ t('backtest.symbol') }}</label>
            <select id="bt-symbol" v-model="form.symbol" required @change="handleSymbolChange">
              <option v-if="symbols.length === 0" value="" disabled>{{ t('backtest.loadingData') }}</option>
              <option v-for="symbol in symbols" :key="symbol" :value="symbol">{{ symbol }}</option>
            </select>
          </div>
          <div class="field">
            <label for="bt-interval">{{ t('backtest.interval') }}</label>
            <select id="bt-interval" v-model="form.dcaInterval" required>
              <option value="WEEKLY">{{ t('backtest.weekly') }}</option>
              <option value="MONTHLY">{{ t('backtest.monthly') }}</option>
            </select>
          </div>
          <div class="field">
            <label for="bt-start">{{ t('backtest.startDate') }}</label>
            <input id="bt-start" v-model="form.startDate" type="date" required :aria-invalid="dateError" />
          </div>
          <div class="field">
            <label for="bt-end">{{ t('backtest.endDate') }}</label>
            <input
              id="bt-end"
              v-model="form.endDate"
              type="date"
              required
              :aria-invalid="dateError"
              :aria-describedby="dateError ? 'bt-date-error' : undefined"
            />
          </div>
          <div class="field">
            <label for="bt-capital">{{ t('backtest.initialCapital') }}</label>
            <input id="bt-capital" v-model.number="form.initialCapital" type="number" min="100" step="100" required />
          </div>
          <div class="field">
            <label for="bt-dca">{{ t('backtest.dcaAmount') }}</label>
            <input id="bt-dca" v-model.number="form.dcaAmount" type="number" min="1" step="50" required />
          </div>
          <p v-if="dateError" id="bt-date-error" class="form-error" role="alert">
            {{ t('backtest.dateError') }}
          </p>
          <div class="form-actions">
            <button class="btn btn--primary run-btn" type="submit" :disabled="isRunning || !canRun">
              <Play v-if="!isRunning" aria-hidden="true" />
              <span v-else class="run-spinner spinner" aria-hidden="true"></span>
              {{ isRunning ? t('backtest.running') : t('backtest.run') }}
            </button>
            <span v-if="dataRange" class="range-note fig">{{ t('backtest.dataRange', { range: `${day(dataRange.start)} → ${day(dataRange.end)}` }) }}</span>
          </div>
        </form>
      </section>

      <section class="block results" aria-labelledby="results-heading" aria-live="polite">
        <div class="block-head">
          <h2 id="results-heading">{{ t('backtest.results') }}</h2>
          <p v-if="result">{{ form.symbol }} · {{ day(form.startDate) }} → {{ day(form.endDate) }}</p>
        </div>

        <template v-if="result">
          <div class="verdict" :key="runId">
            <p class="verdict-line">
              <strong>{{ result.comparison.lumpSumWins ? t('backtest.verdictLump') : t('backtest.verdictDca') }}</strong>
              <span class="verdict-diff">
                {{ t('backtest.difference') }} <span class="fig">{{ formatUsd(Math.abs(result.comparison.dcaDifference), locale) }}</span>
              </span>
            </p>
          </div>

          <div class="results-grid">
            <div class="result-group" :class="{ winner: !result.comparison.lumpSumWins }">
              <h3><i class="key key-dca" aria-hidden="true"></i>{{ t('backtest.dcaStrategy') }}<Check v-if="!result.comparison.lumpSumWins" class="win-mark" aria-hidden="true" /></h3>
              <dl>
                <div>
                  <dt>{{ t('backtest.totalInvested') }}</dt>
                  <dd class="fig">{{ formatUsd(result.dcaStrategy.totalInvested, locale) }}</dd>
                </div>
                <div>
                  <dt>{{ t('backtest.finalValue') }}</dt>
                  <dd class="fig">{{ formatUsd(result.dcaStrategy.finalValue, locale) }}</dd>
                </div>
                <div>
                  <dt>{{ t('backtest.returnPct') }}</dt>
                  <dd class="fig" :class="{ negative: result.dcaStrategy.returnPercentage < 0 }">
                    {{ formatNumber(result.dcaStrategy.returnPercentage, locale) }}%
                  </dd>
                </div>
              </dl>
            </div>
            <div class="result-group" :class="{ winner: result.comparison.lumpSumWins }">
              <h3><i class="key key-lump" aria-hidden="true"></i>{{ t('backtest.lumpSum') }}<Check v-if="result.comparison.lumpSumWins" class="win-mark" aria-hidden="true" /></h3>
              <dl>
                <div>
                  <dt>{{ t('backtest.totalInvested') }}</dt>
                  <dd class="fig">{{ formatUsd(result.lumpSumStrategy.totalInvested, locale) }}</dd>
                </div>
                <div>
                  <dt>{{ t('backtest.finalValue') }}</dt>
                  <dd class="fig">{{ formatUsd(result.lumpSumStrategy.finalValue, locale) }}</dd>
                </div>
                <div>
                  <dt>{{ t('backtest.returnPct') }}</dt>
                  <dd class="fig" :class="{ negative: result.lumpSumStrategy.returnPercentage < 0 }">
                    {{ formatNumber(result.lumpSumStrategy.returnPercentage, locale) }}%
                  </dd>
                </div>
              </dl>
            </div>
          </div>

          <EquityChart
            v-if="curve.length > 1"
            :key="`chart-${runId}`"
            class="curve"
            :points="curve"
            :caption="t('backtest.chartCaption')"
          />
        </template>

        <LoadingSpinner v-else-if="isRunning" :message="t('backtest.running')" />

        <EmptySheet v-else>
          {{ t('backtest.empty') }}
        </EmptySheet>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { db } from '@/services/db/schema'
import { Play, Check } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import TitleBlock from '@/components/pad/TitleBlock.vue'
import EmptySheet from '@/components/pad/EmptySheet.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EquityChart from '@/components/charts/EquityChart.vue'
import { buildEquityCurves, type EquityPoint } from '@/utils/equityCurve'
import { formatNumber, formatSessionDay, formatUsd } from '@/utils/localeFormat'
import { failureMessage } from '@/utils/localizedMessages'

interface BacktestForm {
  symbol: string
  startDate: string
  endDate: string
  initialCapital: number
  dcaAmount: number
  dcaInterval: 'WEEKLY' | 'MONTHLY'
}

interface StrategySummary {
  totalInvested: number
  finalValue: number
  returnPercentage: number
  trades?: { date: string; shares: number; cost: number }[]
}

interface BacktestResult {
  dcaStrategy: StrategySummary
  lumpSumStrategy: StrategySummary
  comparison: {
    dcaFinalValue: number
    lumpSumFinalValue: number
    dcaDifference: number
    lumpSumWins: boolean
  }
}

const { t, locale } = useI18n()
const toast = useToast()
const symbols = ref<string[]>([])
const isRunning = ref(false)
const result = ref<BacktestResult | null>(null)
const curve = ref<EquityPoint[]>([])
const runId = ref(0)
const dataRange = ref<{ start: string; end: string } | null>(null)
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

// 시작일이 종료일보다 늦은 경우 인라인으로 알린다
const dateError = computed(
  () => form.startDate !== '' && form.endDate !== '' && new Date(form.startDate) >= new Date(form.endDate)
)

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
  dataRange.value = { start: form.startDate, end: form.endDate }
}

function handleSymbolChange() {
  updateDateRange()
  result.value = null
  curve.value = []
}

async function runBacktest() {
  if (!canRun.value) {
    toast.warning(t('backtest.invalidInput'))
    return
  }

  try {
    isRunning.value = true
    result.value = null
    curve.value = []

    const candles = await db.candles
      .where('symbol')
      .equals(form.symbol)
      .filter((candle) => candle.date >= form.startDate && candle.date <= form.endDate)
      .toArray()

    if (candles.length < 10) {
      toast.warning(t('backtest.insufficientData'))
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
          reject(new Error(event.data.error ?? ''))
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

    curve.value = buildEquityCurves(
      candles.map((candle) => ({ date: candle.date, close: candle.close })),
      result.value.dcaStrategy.trades ?? [],
      result.value.lumpSumStrategy.trades ?? []
    )
    runId.value += 1

    toast.success(t('backtest.completed'))
  } catch (error) {
    console.error(error)
    // 워커 오류(ErrorEvent)도 Error와 같이 메시지를 사유로 보여준다
    const detail = error instanceof Error || error instanceof ErrorEvent ? error.message : null
    toast.error(failureMessage('backtest.failed', detail))
  } finally {
    isRunning.value = false
  }
}

// 표시용 날짜 (입력 필드의 YYYY-MM-DD 값은 그대로 둔다)
function day(value: string): string {
  return value ? formatSessionDay(value, locale.value) : ''
}

onMounted(() => {
  loadSymbols().catch((error) => {
    console.error(error)
    toast.error(failureMessage('backtest.symbolsFailed', error instanceof Error ? error.message : null))
  })
})
</script>

<style scoped>
.lab-grid {
  display: grid;
  grid-template-columns: minmax(0, 4fr) minmax(0, 8fr);
  gap: var(--space-12);
  align-items: start;
}

.lab-grid .block + .block {
  margin-top: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-5) var(--space-4);
}

.form-error {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--red-pencil);
  font-size: var(--text-sm);
}

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.run-btn {
  min-height: 52px;
  font-size: var(--text-lg);
}

.run-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
}

.range-note {
  font-size: var(--text-sm);
  line-height: 1.5;
  color: var(--ink-2);
}

/* the answer, boxed by hand once the run lands: the box is drawn left to right */
.verdict {
  position: relative;
  display: inline-block;
  max-width: 100%;
  margin: var(--space-2) 0 var(--space-6);
  padding: var(--space-3) var(--space-5);
  background: linear-gradient(transparent 55%, var(--highlight) 55%, var(--highlight) 92%, transparent 92%);
}

.verdict::after {
  content: '';
  position: absolute;
  inset: 0;
  border: 2px solid var(--ink);
  pointer-events: none;
  animation: box-draw 700ms var(--ease-out) 700ms both;
}

@keyframes box-draw {
  from {
    clip-path: inset(0 100% 0 0);
  }
  to {
    clip-path: inset(0 0 0 0);
  }
}

@media (prefers-color-scheme: dark) {
  .verdict {
    background: var(--highlight);
  }

  .verdict::after {
    border-color: var(--highlight);
  }
}

.verdict-line {
  position: relative;
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-2) var(--space-4);
  color: var(--highlight-ink);
}

.verdict-line strong {
  font-family: var(--font-label);
  font-size: var(--text-2xl);
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.verdict-diff {
  font-size: var(--text-base);
}

.results-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-8);
  margin-bottom: var(--space-8);
}

.result-group h3 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: var(--space-2);
  border-bottom: 1.5px solid var(--rule-strong);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.win-mark {
  margin-left: auto;
  width: 20px;
  height: 20px;
}

.result-group dl {
  margin: 0;
}

.result-group dl div {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--rule);
}

.result-group dt {
  color: var(--ink-2);
  font-size: var(--text-sm);
}

.result-group dd {
  margin: 0;
  font-size: var(--text-lg);
}

.key {
  display: inline-block;
  width: 18px;
  border-top: 2.5px solid var(--series-1);
}

.key-lump {
  border-top-color: var(--series-2);
}

@media (max-width: 1080px) {
  .lab-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 560px) {
  .form-grid,
  .results-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .results-grid {
    gap: var(--space-6);
  }
}
</style>
