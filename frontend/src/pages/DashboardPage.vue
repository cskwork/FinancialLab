<template>
  <div class="dashboard-page">
    <div class="dashboard-container">
      <header class="dashboard-header">
        <div>
          <h1>Dashboard</h1>
          <p v-if="portfolioStore.currentPortfolio" class="portfolio-name">
            {{ portfolioStore.currentPortfolio.name }}
          </p>
        </div>

        <div class="portfolio-selector" v-if="portfolioStore.portfolios.length > 0">
          <label for="portfolio-select">Portfolio:</label>
          <select
            id="portfolio-select"
            v-model="selectedPortfolioId"
            @change="handlePortfolioChange"
          >
            <option
              v-for="portfolio in portfolioStore.portfolios"
              :key="portfolio.id"
              :value="portfolio.id"
            >
              {{ portfolio.name }} ({{ portfolio.baseCurrency }})
            </option>
          </select>
        </div>
      </header>

      <LoadingSpinner v-if="isLoading" fullscreen message="Loading portfolio data..." />

      <div v-else-if="!authStore.isAuthenticated" class="empty-state">
        <p>Please login to view your portfolio</p>
        <router-link to="/login" class="btn-primary">Go to Login</router-link>
      </div>

      <div v-else-if="!portfolioStore.hasPortfolios" class="empty-state">
        <p>No portfolios found. Create one to get started.</p>
        <button @click="createDemoPortfolio" class="btn-primary">Create Demo Portfolio</button>
      </div>

      <div v-else class="dashboard-content">
        <!-- Summary Cards -->
        <section class="summary-section">
          <Card>
            <template #header>
              <h2>Portfolio Summary</h2>
            </template>
            <div class="summary-grid">
              <div class="summary-item">
                <span class="summary-label">Total Value</span>
                <span class="summary-value">
                  {{ portfolioMetrics ? `$${formatCurrency(portfolioMetrics.totalValue)}` : '--' }}
                </span>
              </div>
              <div class="summary-item">
                <span class="summary-label">Total Holdings</span>
                <span class="summary-value">{{ holdingsCount }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">Total Transactions</span>
                <span class="summary-value">{{ transactionStore.transactions.length }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">Daily PnL</span>
                <span class="summary-value" :class="{ positive: (portfolioMetrics?.pnlDaily ?? 0) > 0, negative: (portfolioMetrics?.pnlDaily ?? 0) < 0 }">
                  {{
                    portfolioMetrics
                      ? `${portfolioMetrics.pnlDaily >= 0 ? '+' : '-'}$${formatCurrency(Math.abs(portfolioMetrics.pnlDaily))}`
                      : '--'
                  }}
                </span>
              </div>
              <div class="summary-item">
                <span class="summary-label">Sharpe Ratio</span>
                <span class="summary-value">{{ portfolioMetrics ? portfolioMetrics.sharpe.toFixed(2) : '--' }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">Volatility</span>
                <span class="summary-value">{{ portfolioMetrics ? `${portfolioMetrics.volatility.toFixed(2)}%` : '--' }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">Base Currency</span>
                <span class="summary-value">{{ portfolioStore.currentPortfolio?.baseCurrency }}</span>
              </div>
            </div>
          </Card>
        </section>

        <!-- Holdings Table -->
        <section class="holdings-section">
          <Card>
            <template #header>
              <h2>Current Holdings (FIFO)</h2>
            </template>
            <div v-if="holdings.length === 0" class="empty-message">
              <p>No holdings yet. Add some buy transactions to see them here.</p>
            </div>
            <div v-else class="table-container">
              <table class="holdings-table">
                <thead>
                  <tr>
                    <th>Symbol</th>
                    <th>Shares</th>
                    <th>Avg Cost</th>
                    <th>Market Value</th>
                    <th>Unrealized PnL</th>
                    <th>Updated</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="holding in holdings" :key="holding.symbol">
                    <td class="symbol">{{ holding.symbol }}</td>
                    <td>{{ holding.shares.toFixed(2) }}</td>
                    <td>${{ holding.avgCost.toFixed(2) }}</td>
                    <td>${{ formatCurrency(holding.marketValue) }}</td>
                    <td :class="{ positive: holding.unrealizedPnL >= 0, negative: holding.unrealizedPnL < 0 }">
                      {{ holding.unrealizedPnL >= 0 ? '+' : '-' }}${{
                        formatCurrency(Math.abs(holding.unrealizedPnL))
                      }}
                    </td>
                    <td>{{ formatDate(holding.lastUpdated) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </section>

        <!-- Transactions Preview -->
        <section class="transactions-preview">
          <Card>
            <template #header>
              <div class="section-header">
                <h2>Recent Transactions</h2>
                <router-link to="/transactions" class="link-view-all">View All →</router-link>
              </div>
            </template>
            <div v-if="transactionStore.sortedTransactions.length === 0" class="empty-message">
              <p>No transactions yet.</p>
            </div>
            <div v-else class="transactions-list">
              <div
                v-for="tx in recentTransactions"
                :key="tx.id"
                class="transaction-item"
                :class="`tx-${tx.type.toLowerCase()}`"
              >
                <div class="tx-info">
                  <span class="tx-type">{{ tx.type }}</span>
                  <span v-if="tx.symbol" class="tx-symbol">{{ tx.symbol }}</span>
                  <span v-if="tx.qty" class="tx-qty">{{ tx.qty }} shares</span>
                  <span v-if="tx.price" class="tx-price">${{ tx.price.toFixed(2) }}</span>
                </div>
                <div class="tx-date">
                  {{ formatDate(tx.timestamp) }}
                </div>
              </div>
            </div>
          </Card>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { useAnalyticsStore } from '@/stores/analyticsStore'
import Card from '@/components/common/Card.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const router = useRouter()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const transactionStore = useTransactionStore()
const analyticsStore = useAnalyticsStore()

const isLoading = ref(false)
const selectedPortfolioId = ref<number | null>(null)

// Computed
const holdingsCount = computed(() => analyticsStore.holdings.length)

const holdings = computed(() => analyticsStore.holdings)

const portfolioMetrics = computed(() => analyticsStore.metrics)

const recentTransactions = computed(() => {
  return transactionStore.sortedTransactions.slice(0, 5)
})

// 데이터 로드
async function loadData() {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return
  }

  isLoading.value = true

  try {
    // 포트폴리오 목록 조회
    await portfolioStore.fetchPortfolios()

    // 현재 포트폴리오가 있으면 거래 내역 조회
    if (portfolioStore.currentPortfolio) {
      selectedPortfolioId.value = portfolioStore.currentPortfolio.id
      await transactionStore.fetchTransactions(portfolioStore.currentPortfolio.id)
      await analyticsStore.fetchAnalytics(portfolioStore.currentPortfolio.id)
    }
  } catch (error) {
    console.error('Failed to load data:', error)
  } finally {
    isLoading.value = false
  }
}

// 포트폴리오 변경
async function handlePortfolioChange() {
  if (selectedPortfolioId.value) {
    const portfolio = portfolioStore.portfolios.find((p) => p.id === selectedPortfolioId.value)
    if (portfolio) {
      portfolioStore.setCurrentPortfolio(portfolio)
      await transactionStore.fetchTransactions(portfolio.id)
      await analyticsStore.fetchAnalytics(portfolio.id)
    }
  }
}

// 데모 포트폴리오 생성
async function createDemoPortfolio() {
  const success = await portfolioStore.createPortfolio('Demo Portfolio', 'USD')
  if (success) {
    await loadData()
  }
}

// 날짜 포맷
function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatCurrency(value: number): string {
  return value.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

// 초기 로드
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.dashboard-page {
  min-height: 100vh;
  background-color: var(--color-gray-50);
  padding: var(--space-6);
}

.dashboard-container {
  max-width: 1400px;
  margin: 0 auto;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
  gap: var(--space-4);
}

.dashboard-header h1 {
  margin: 0;
  font-size: var(--font-size-3xl);
  color: var(--color-text);
}

.portfolio-name {
  margin: var(--space-2) 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-base);
}

.portfolio-selector {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.portfolio-selector label {
  font-weight: var(--font-weight-medium);
  color: var(--color-text-muted);
}

.portfolio-selector select {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-base);
  background-color: var(--color-background);
  cursor: pointer;
}

.empty-state {
  text-align: center;
  padding: var(--space-16) var(--space-8);
}

.empty-state p {
  font-size: var(--font-size-lg);
  color: var(--color-text-muted);
  margin-bottom: var(--space-6);
}

.btn-primary {
  display: inline-block;
  padding: var(--space-3) var(--space-6);
  background-color: var(--color-primary-600);
  color: white;
  border: none;
  border-radius: var(--border-radius-md);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  text-decoration: none;
  transition: all var(--transition-base);
}

.btn-primary:hover {
  background-color: var(--color-primary-700);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.dashboard-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.summary-section h2,
.holdings-section h2,
.transactions-preview h2 {
  margin: 0;
  font-size: var(--font-size-xl);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-6);
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.summary-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.summary-value {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text);
}

.summary-value.positive {
  color: var(--color-success-600);
}

.summary-value.negative {
  color: var(--color-error-600);
}

.table-container {
  overflow-x: auto;
}

.holdings-table {
  width: 100%;
  border-collapse: collapse;
}

.holdings-table th {
  text-align: left;
  padding: var(--space-3);
  border-bottom: 2px solid var(--color-border);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  text-transform: uppercase;
}

.holdings-table td {
  padding: var(--space-3);
  border-bottom: 1px solid var(--color-border);
}

.holdings-table tbody tr:hover {
  background-color: var(--color-gray-50);
}

.holdings-table .symbol {
  font-weight: var(--font-weight-medium);
  color: var(--color-primary-600);
}

.holdings-table td.positive {
  color: var(--color-success-600);
}

.holdings-table td.negative {
  color: var(--color-error-600);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.link-view-all {
  font-size: var(--font-size-sm);
  color: var(--color-primary-600);
  text-decoration: none;
  font-weight: var(--font-weight-medium);
}

.link-view-all:hover {
  text-decoration: underline;
}

.transactions-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.transaction-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3);
  border-radius: var(--border-radius-md);
  background-color: var(--color-gray-50);
}

.tx-info {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

.tx-type {
  font-weight: var(--font-weight-bold);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  text-transform: uppercase;
}

.tx-buy .tx-type {
  background-color: var(--color-success-100);
  color: var(--color-success-700);
}

.tx-sell .tx-type {
  background-color: var(--color-error-100);
  color: var(--color-error-700);
}

.tx-deposit .tx-type,
.tx-withdraw .tx-type {
  background-color: var(--color-info-100);
  color: var(--color-info-700);
}

.tx-symbol {
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
}

.tx-qty,
.tx-price {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.tx-date {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.empty-message {
  text-align: center;
  padding: var(--space-8);
  color: var(--color-text-muted);
}

@media (max-width: 768px) {
  .dashboard-page {
    padding: var(--space-4);
  }

  .dashboard-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .transaction-item {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }
}
</style>
