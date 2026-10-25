<template>
  <div class="page dashboard-page">
    <TitleBlock :title="t('dashboard.title')" sheet="01 / 04" :sheet-label="t('pad.sheet')">
      <template v-if="portfolioStore.currentPortfolio" #default>
        {{ portfolioStore.currentPortfolio.name }}
      </template>
      <template v-if="portfolioStore.portfolios.length > 0" #actions>
        <div class="field portfolio-selector">
          <label for="portfolio-select">{{ t('dashboard.portfolio') }}</label>
          <select id="portfolio-select" v-model="selectedPortfolioId" @change="handlePortfolioChange">
            <option v-for="portfolio in portfolioStore.portfolios" :key="portfolio.id" :value="portfolio.id">
              {{ portfolio.name }} ({{ portfolio.baseCurrency }})
            </option>
          </select>
        </div>
      </template>
    </TitleBlock>

    <LoadingSpinner v-if="isLoading" fullscreen :message="t('dashboard.loadingPortfolio')" />

    <EmptySheet v-else-if="!authStore.isAuthenticated">
      {{ t('dashboard.pleaseLogin') }}
      <template #action>
        <router-link to="/login" class="btn btn--primary">{{ t('dashboard.goToLogin') }}</router-link>
      </template>
    </EmptySheet>

    <EmptySheet v-else-if="!portfolioStore.hasPortfolios">
      {{ t('dashboard.noPortfolios') }}
      <template #action>
        <button type="button" class="btn btn--primary" @click="createDemoPortfolio">
          {{ t('dashboard.createDemoPortfolio') }}
        </button>
      </template>
    </EmptySheet>

    <div v-else class="dashboard-content">
      <p class="sample-notice">{{ t('dashboard.sampleNotice') }}</p>
      <p v-if="sampleAutoSelected" class="sample-notice" role="status">{{ t('dashboard.sampleAutoSelected') }}</p>

      <div v-if="loadError" class="load-error" role="alert">
        <p>{{ loadError }}</p>
        <button class="btn" type="button" @click="loadData">{{ t('dashboard.retry') }}</button>
      </div>

      <!-- 거래가 없는 포트폴리오: 의미 없는 '--' 대신 이유와 다음 행동을 보여준다 -->
      <EmptySheet v-if="isEmptyPortfolio">
        {{ t('dashboard.emptyPortfolio', { name: portfolioStore.currentPortfolio?.name ?? '' }) }}
        <template #action>
          <div v-if="configStore.isMockMode" class="sample-offer">
            <p id="sample-hint" class="sample-hint">
              {{ isDemoAccount ? t('dashboard.sampleHint') : t('dashboard.sampleHintSwitch', { email: DEMO_EMAIL }) }}
            </p>
            <div class="sample-actions">
              <button
                type="button"
                class="btn btn--primary"
                aria-describedby="sample-hint"
                :disabled="isOpeningSample"
                :aria-busy="isOpeningSample"
                @click="openSamplePortfolio"
              >
                {{ isOpeningSample ? t('dashboard.openingSample') : t('dashboard.viewSamplePortfolio') }}
              </button>
              <router-link to="/transactions" class="btn">{{ t('dashboard.addTransaction') }}</router-link>
            </div>
          </div>
          <router-link v-else to="/transactions" class="btn btn--primary">{{ t('dashboard.addTransaction') }}</router-link>
        </template>
      </EmptySheet>

      <template v-else>
        <aside class="beginner-banner" role="note">
          <p>{{ t('dashboard.beginnerNotice') }}</p>
        </aside>

        <div class="dash-grid">
          <!-- Summary: the working, with the answer boxed -->
          <section class="block summary-section" aria-labelledby="summary-heading">
            <div class="block-head">
              <h2 id="summary-heading">{{ t('dashboard.summary') }}</h2>
            </div>
            <div class="total">
              <span class="label">
                {{ t('dashboard.totalValue') }}
                <HelpTooltip :text="t('terms.marketValue')" :term="t('dashboard.totalValue')" />
              </span>
              <span class="total-value fig">
                <span class="answer">{{ portfolioMetrics ? formatUsd(portfolioMetrics.totalValue, locale) : '--' }}</span>
              </span>
            </div>
            <dl class="calc">
              <div class="calc-row">
                <dt>
                  {{ t('dashboard.dailyPnL') }}
                  <HelpTooltip :text="t('terms.dailyPnL')" :term="t('dashboard.dailyPnL')" />
                </dt>
                <dd
                  class="fig"
                  :class="{ positive: (portfolioMetrics?.pnlDaily ?? 0) > 0, negative: (portfolioMetrics?.pnlDaily ?? 0) < 0 }"
                >
                  {{ portfolioMetrics ? formatSignedUsd(portfolioMetrics.pnlDaily, locale) : '--' }}
                </dd>
              </div>
              <div class="calc-row">
                <dt>
                  {{ t('dashboard.sharpeRatio') }}
                  <HelpTooltip :text="t('terms.sharpe')" :term="t('dashboard.sharpeRatio')" :tip="t('terms.sharpeTip')" />
                </dt>
                <dd class="fig">
                  {{ portfolioMetrics ? formatNumber(portfolioMetrics.sharpe, locale) : '--' }}
                  <span v-if="portfolioMetrics && getSharpeBadge(portfolioMetrics.sharpe)" :class="['badge-status', getSharpeBadge(portfolioMetrics.sharpe)!.class]">
                    {{ getSharpeBadge(portfolioMetrics.sharpe)!.label }}
                  </span>
                </dd>
              </div>
              <div class="calc-row">
                <dt>
                  {{ t('dashboard.volatility') }}
                  <HelpTooltip :text="t('terms.volatility')" :term="t('dashboard.volatility')" :tip="t('terms.volatilityTip')" />
                </dt>
                <dd class="fig">
                  {{ portfolioMetrics ? `${formatNumber(portfolioMetrics.volatility, locale)}%` : '--' }}
                  <span v-if="portfolioMetrics && getVolatilityBadge(portfolioMetrics.volatility)" :class="['badge-status', getVolatilityBadge(portfolioMetrics.volatility)!.class]">
                    {{ getVolatilityBadge(portfolioMetrics.volatility)!.label }}
                  </span>
                </dd>
              </div>
              <div class="calc-row">
                <dt>{{ t('dashboard.totalHoldings') }}</dt>
                <dd class="fig">{{ holdingsCount }}</dd>
              </div>
              <div class="calc-row">
                <dt>{{ t('dashboard.totalTransactions') }}</dt>
                <dd class="fig">{{ transactionStore.transactions.length }}</dd>
              </div>
              <div class="calc-row">
                <dt>{{ t('dashboard.baseCurrency') }}</dt>
                <dd class="fig">{{ portfolioStore.currentPortfolio?.baseCurrency }}</dd>
              </div>
            </dl>
          </section>

          <!-- Holdings ledger -->
          <section class="block holdings-section" aria-labelledby="holdings-heading">
            <div class="block-head">
              <h2 id="holdings-heading">
                {{ t('dashboard.currentHoldings') }}
                <HelpTooltip :text="t('terms.fifo')" term="FIFO" :tip="t('terms.fifoTip')" />
              </h2>
            </div>
            <EmptySheet v-if="holdings.length === 0" compact>{{ t('dashboard.noHoldings') }}</EmptySheet>
            <p v-if="holdings.length" class="table-hint">{{ t('dashboard.tableHint') }}</p>
            <div
              v-if="holdings.length"
              class="ledger-scroll"
              tabindex="0"
              role="region"
              :aria-label="t('dashboard.currentHoldings')"
            >
              <table class="holdings-table">
                <thead>
                  <tr>
                    <th scope="col">{{ t('dashboard.symbol') }}</th>
                    <th scope="col" class="num">{{ t('dashboard.shares') }}</th>
                    <th scope="col" class="num">
                      {{ t('dashboard.avgCost') }}
                      <HelpTooltip :text="t('terms.avgCost')" :term="t('dashboard.avgCost')" />
                    </th>
                    <th scope="col" class="num">
                      {{ t('dashboard.marketValue') }}
                      <HelpTooltip :text="t('terms.marketValue')" :term="t('dashboard.marketValue')" />
                    </th>
                    <th scope="col" class="num">
                      {{ t('dashboard.weight') }}
                      <HelpTooltip :text="t('terms.weight')" :term="t('dashboard.weight')" />
                    </th>
                    <th scope="col" class="num">
                      {{ t('dashboard.unrealizedPnL') }}
                      <HelpTooltip :text="t('terms.unrealizedPnL')" :term="t('dashboard.unrealizedPnL')" />
                    </th>
                    <th scope="col">{{ t('dashboard.updated') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="holding in holdings" :key="holding.symbol">
                    <th scope="row" class="symbol">{{ holding.symbol }}</th>
                    <td class="num">{{ formatNumber(holding.shares, locale) }}</td>
                    <td class="num">{{ formatUsd(holding.avgCost, locale) }}</td>
                    <td class="num">{{ formatUsd(holding.marketValue, locale) }}</td>
                    <td class="num">
                      <span class="weight">
                        <span class="weight-bar" :style="{ width: `${weightOf(holding.marketValue)}%` }"></span>
                        {{ formatNumber(weightOf(holding.marketValue), locale, 1) }}%
                      </span>
                    </td>
                    <td class="num" :class="{ positive: holding.unrealizedPnL >= 0, negative: holding.unrealizedPnL < 0 }">
                      {{ formatSignedUsd(holding.unrealizedPnL, locale) }}
                    </td>
                    <td class="updated">{{ formatDateTime(holding.lastUpdated, locale) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>

        <!-- Recent transactions -->
        <section class="block transactions-preview" aria-labelledby="recent-heading">
          <div class="block-head">
            <h2 id="recent-heading">{{ t('dashboard.recentTransactions') }}</h2>
            <router-link to="/transactions" class="link-view-all">{{ t('dashboard.viewAll') }}</router-link>
          </div>
          <EmptySheet v-if="transactionStore.sortedTransactions.length === 0" compact>
            {{ t('dashboard.noTransactions') }}
          </EmptySheet>
          <ol v-else class="tx-lines">
            <li v-for="tx in recentTransactions" :key="tx.id" class="tx-line">
              <span class="tx-type" :class="`tx-${tx.type.toLowerCase()}`">{{ transactionTypeLabel(tx.type) }}</span>
              <span class="tx-symbol">{{ tx.symbol ?? '—' }}</span>
              <span class="tx-qty fig">{{ tx.qty ? `${tx.qty} ${t('dashboard.sharesUnit')}` : '' }}</span>
              <span class="tx-price fig">{{ tx.price ? formatUsd(tx.price, locale) : '' }}</span>
              <span class="tx-date fig">{{ formatDateTime(tx.timestamp, locale) }}</span>
            </li>
          </ol>
        </section>
      </template>
    </div>
  </div>
</template>

<script lang="ts">
// 예시 포트폴리오 자동 선택은 페이지 세션당 한 번만: 사용자가 빈 포트폴리오를 직접 고르면 다시 바꾸지 않는다
let sampleAutoSelectChecked = false
</script>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/authStore'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { useAnalyticsStore } from '@/stores/analyticsStore'
import { useGoalPlanStore } from '@/stores/goalPlanStore'
import { useConfigStore } from '@/stores/configStore'
import { DEMO_EMAIL, ensureDemoPortfolio } from '@/utils/seedLoader'
import { logger } from '@/utils/logger'
import { formatDateTime, formatNumber, formatSignedUsd, formatUsd } from '@/utils/localeFormat'
import { failureMessage, transactionTypeLabel } from '@/utils/localizedMessages'
import { useToast } from '@/composables/useToast'
import TitleBlock from '@/components/pad/TitleBlock.vue'
import EmptySheet from '@/components/pad/EmptySheet.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import HelpTooltip from '@/components/common/HelpTooltip.vue'

const router = useRouter()
const { t, locale } = useI18n()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const transactionStore = useTransactionStore()
const analyticsStore = useAnalyticsStore()
const goalPlanStore = useGoalPlanStore()
const configStore = useConfigStore()
const toast = useToast()

const isLoading = ref(false)
const isOpeningSample = ref(false)
const sampleAutoSelected = ref(false)
// 화면 오류는 키와 원문 사유로 보관해 언어를 바꿔도 현재 언어로 다시 그린다
const sampleError = ref<{ key: string; detail?: string | null } | null>(null)
const selectedPortfolioId = ref<number | null>(null)

// Computed
const holdingsCount = computed(() => analyticsStore.holdings.length)

const holdings = computed(() => analyticsStore.holdings)

const portfolioMetrics = computed(() => analyticsStore.metrics)

const recentTransactions = computed(() => {
  return transactionStore.sortedTransactions.slice(0, 5)
})

const isDemoAccount = computed(() => authStore.currentUserEmail?.toLowerCase() === DEMO_EMAIL)

const isEmptyPortfolio = computed(
  () => !!portfolioStore.currentPortfolio && !transactionStore.error && transactionStore.transactions.length === 0
)

// 입금만 있는 등 보유 종목이 없으면 수익률 계열도 없다: metricsWorker의 'No returns data'는 오류가 아니라 빈 상태
const NO_RETURNS_ERROR = 'No returns data'
const analyticsError = computed(() =>
  holdings.value.length === 0 && analyticsStore.error === NO_RETURNS_ERROR ? null : analyticsStore.error
)

// 스토어 오류는 저장된 원문을 두고 표시할 때만 현재 언어로 바꾼다
const loadError = computed(() => {
  if (sampleError.value) return failureMessage(sampleError.value.key, sampleError.value.detail)
  const storeError = portfolioStore.error || transactionStore.error || analyticsError.value
  return storeError ? failureMessage('dashboard.loadFailed', storeError) : null
})

function getSharpeBadge(sharpe: number | undefined): { label: string; class: string } | null {
  if (sharpe === undefined || Number.isNaN(sharpe)) return null
  if (sharpe >= 2) return { label: t('badges.sharpeExcellent'), class: 'badge-status--good' }
  if (sharpe >= 1) return { label: t('badges.sharpeGood'), class: 'badge-status--neutral' }
  return { label: t('badges.sharpeFair'), class: 'badge-status--warn' }
}

function getVolatilityBadge(vol: number | undefined): { label: string; class: string } | null {
  if (vol === undefined || Number.isNaN(vol)) return null
  if (vol < 15) return { label: t('badges.volLow'), class: 'badge-status--good' }
  if (vol <= 25) return { label: t('badges.volModerate'), class: 'badge-status--neutral' }
  return { label: t('badges.volHigh'), class: 'badge-status--warn' }
}

// 선택한 포트폴리오의 거래와 지표 조회 (거래가 없으면 지표 계산을 건너뛴다)
async function loadPortfolio(portfolioId: number) {
  selectedPortfolioId.value = portfolioId
  await transactionStore.fetchTransactions(portfolioId)
  // 이전 포트폴리오의 보유 종목/지표가 남지 않도록 비운 뒤 조회
  analyticsStore.reset()
  if (transactionStore.transactions.length === 0) return
  await analyticsStore.fetchAnalytics(portfolioId)
}

function selectPortfolioById(portfolioId: number): boolean {
  const portfolio = portfolioStore.portfolios.find((p) => p.id === portfolioId)
  if (!portfolio) return false
  portfolioStore.setCurrentPortfolio(portfolio)
  return true
}

// 데모 계정의 첫 화면이 빈 포트폴리오면 시드된 예시 포트폴리오를 '선택'만 한다 (기존 포트폴리오에 데이터를 채우지 않음)
async function autoSelectSamplePortfolio() {
  if (sampleAutoSelectChecked || !configStore.isMockMode || !isDemoAccount.value) return
  sampleAutoSelectChecked = true
  if (transactionStore.transactions.length > 0) return

  const { demoPortfolioId } = await ensureDemoPortfolio()
  if (demoPortfolioId === portfolioStore.currentPortfolio?.id) return
  if (!portfolioStore.portfolios.some((p) => p.id === demoPortfolioId)) {
    await portfolioStore.fetchPortfolios()
  }
  if (selectPortfolioById(demoPortfolioId)) {
    sampleAutoSelected.value = true
    await loadPortfolio(demoPortfolioId)
  }
}

// 데이터 로드
async function loadData() {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return
  }

  isLoading.value = true
  sampleError.value = null

  try {
    // 포트폴리오 목록 조회
    await portfolioStore.fetchPortfolios()

    // 현재 포트폴리오가 있으면 거래 내역 조회
    if (portfolioStore.currentPortfolio) {
      await loadPortfolio(portfolioStore.currentPortfolio.id)
      await autoSelectSamplePortfolio()
    }
  } catch (error) {
    console.error('Failed to load data:', error)
    sampleError.value = { key: 'dashboard.loadFailed', detail: error instanceof Error ? error.message : null }
  } finally {
    isLoading.value = false
  }
}

// 포트폴리오 변경
async function handlePortfolioChange() {
  if (selectedPortfolioId.value && selectPortfolioById(selectedPortfolioId.value)) {
    sampleAutoSelected.value = false
    await loadPortfolio(selectedPortfolioId.value)
  }
}

/**
 * 예시 포트폴리오 보기: 시드 헬퍼(멱등)로 2024년 샘플 포트폴리오를 확인/복구한 뒤 선택한다.
 * 데모 계정이 아니면 사용자가 버튼으로 동의한 경우에만 데모 계정으로 전환한다. 어떤 기록도 지우지 않는다.
 */
async function openSamplePortfolio() {
  if (isOpeningSample.value) return
  isOpeningSample.value = true
  isLoading.value = true
  sampleError.value = null

  try {
    const { demoPortfolioId } = await ensureDemoPortfolio()

    if (!isDemoAccount.value) {
      authStore.logout()
      portfolioStore.reset()
      transactionStore.reset()
      goalPlanStore.reset()
      analyticsStore.reset()
      if (!(await authStore.login(DEMO_EMAIL))) {
        sampleError.value = { key: 'dashboard.sampleLoadFailed', detail: authStore.error }
        router.push('/login')
        return
      }
    }

    await portfolioStore.fetchPortfolios()
    if (!selectPortfolioById(demoPortfolioId)) {
      sampleError.value = { key: 'dashboard.sampleLoadFailed' }
      return
    }
    sampleAutoSelected.value = false
    await loadPortfolio(demoPortfolioId)
  } catch (error) {
    logger.error('Failed to open sample portfolio', error)
    sampleError.value = { key: 'dashboard.sampleLoadFailed' }
  } finally {
    isOpeningSample.value = false
    isLoading.value = false
  }
}

// 데모 포트폴리오 생성
async function createDemoPortfolio() {
  const success = await portfolioStore.createPortfolio('Demo Portfolio', 'USD')
  if (success) {
    await loadData()
  } else {
    // 포트폴리오가 없는 화면에는 오류 줄이 없으므로 toast로 알린다
    toast.error(failureMessage('dashboard.createPortfolioFailed', portfolioStore.error))
  }
}

// 보유 비중 (%): 시장가치 / 총 가치
function weightOf(marketValue: number): number {
  const total = portfolioMetrics.value?.totalValue ?? 0
  return total > 0 ? (marketValue / total) * 100 : 0
}

// 초기 로드
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.dash-grid {
  display: grid;
  grid-template-columns: minmax(0, 4fr) minmax(0, 8fr);
  gap: var(--space-12);
  align-items: start;
}

.dash-grid .block + .block {
  margin-top: 0;
}

.portfolio-selector {
  min-width: 14rem;
}

.sample-notice {
  margin-bottom: var(--space-6);
  color: var(--ink-2);
  font-size: var(--text-sm);
}

.load-error {
  border: 1.5px solid var(--red-pencil);
  padding: var(--space-4);
  margin-bottom: var(--space-6);
}

.table-hint {
  color: var(--ink-2);
  font-size: var(--text-sm);
}

.sample-hint {
  max-width: 40rem;
  margin-bottom: var(--space-4);
  color: var(--ink-2);
  font-size: var(--text-sm);
}

.sample-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.total {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) 0 var(--space-6);
}

.total-value {
  font-size: clamp(1.75rem, 1.2rem + 1.6vw, 2.5rem);
  font-weight: 600;
  line-height: 1.1;
}

.calc {
  margin: 0;
}

.calc-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--space-4);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--rule);
}

.calc-row dt {
  color: var(--ink-2);
  font-size: var(--text-sm);
}

.calc-row dd {
  margin: 0;
  font-size: var(--text-lg);
}

.holdings-table .symbol {
  font-family: var(--font-label);
  font-size: var(--text-lg);
  font-weight: 700;
  letter-spacing: 0.02em;
  text-transform: none;
  color: var(--ink);
  border-bottom: 1px solid var(--rule);
}

.weight {
  position: relative;
  display: inline-block;
  min-width: 5.5rem;
  padding-bottom: 6px;
}

.weight-bar {
  position: absolute;
  right: 0;
  bottom: 0;
  height: 3px;
  background-color: var(--ink);
  max-width: 100%;
}

.updated {
  color: var(--ink-2);
  font-size: var(--text-sm);
  white-space: nowrap;
}

.transactions-preview {
  margin-top: var(--space-16);
}

.link-view-all {
  font-weight: 600;
}

.tx-lines {
  list-style: none;
}

.tx-line {
  display: grid;
  grid-template-columns: 7rem 5rem minmax(0, 1fr) 7rem 13rem;
  gap: var(--space-4);
  align-items: baseline;
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--rule);
}

.tx-type {
  font-family: var(--font-label);
  font-weight: 700;
  font-size: var(--text-sm);
  letter-spacing: var(--tracking-label);
  text-transform: uppercase;
}

.tx-sell {
  color: var(--red-pencil);
}

.tx-deposit,
.tx-withdraw {
  color: var(--blue-pencil);
}

.tx-symbol {
  font-weight: 600;
}

.tx-qty,
.tx-price {
  color: var(--ink-2);
  text-align: right;
}

.tx-date {
  color: var(--ink-2);
  font-size: var(--text-xs);
  text-align: right;
}

@media (max-width: 1080px) {
  .dash-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-12);
  }
}

@media (max-width: 720px) {
  .portfolio-selector {
    min-width: 0;
    width: 100%;
  }

  .tx-line {
    grid-template-columns: auto auto minmax(0, 1fr);
    grid-template-areas:
      'type symbol price'
      'qty qty date';
    row-gap: var(--space-1);
  }

  .tx-type { grid-area: type; }
  .tx-symbol { grid-area: symbol; }
  .tx-price { grid-area: price; }
  .tx-qty { grid-area: qty; text-align: left; }
  .tx-date { grid-area: date; }
}
</style>
