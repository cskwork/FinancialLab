<template>
  <div class="page transactions-page">
    <TitleBlock title="Transactions" sheet="02 / 04">
      <template v-if="portfolioStore.currentPortfolio" #default>{{ portfolioStore.currentPortfolio.name }}</template>
      <template #actions>
        <button
          type="button"
          class="btn"
          :class="showAddForm ? '' : 'btn--primary'"
          :aria-expanded="showAddForm"
          aria-controls="tx-entry"
          @click="showAddForm = !showAddForm"
        >
          <component :is="showAddForm ? X : Plus" aria-hidden="true" />
          {{ showAddForm ? 'Cancel' : 'Add Transaction' }}
        </button>
        <button type="button" class="btn" :disabled="isImporting" @click="triggerCsvImport">
          <Upload aria-hidden="true" />
          {{ isImporting ? `Importing ${importProgress}%` : 'Import CSV' }}
        </button>
        <input
          ref="fileInputRef"
          type="file"
          accept=".csv"
          class="visually-hidden"
          tabindex="-1"
          aria-hidden="true"
          @change="handleCsvFile"
        />
      </template>
    </TitleBlock>

    <LoadingSpinner v-if="isLoading" fullscreen message="Loading transactions..." />

    <EmptySheet v-else-if="!authStore.isAuthenticated">
      Please login to view transactions
      <template #action>
        <router-link to="/login" class="btn btn--primary">Go to Login</router-link>
      </template>
    </EmptySheet>

    <EmptySheet v-else-if="!portfolioStore.currentPortfolio">
      No portfolio selected. Go to dashboard to create one.
      <template #action>
        <router-link to="/dashboard" class="btn btn--primary">Go to Dashboard</router-link>
      </template>
    </EmptySheet>

    <div v-else class="transactions-content">
      <!-- New entry: the ledger's next blank line -->
      <section v-if="showAddForm" id="tx-entry" class="block entry" aria-labelledby="entry-heading">
        <div class="block-head">
          <h2 id="entry-heading">Add New Transaction</h2>
          <p>Fields marked * are required.</p>
        </div>
        <form class="entry-line" @submit.prevent="handleAddTransaction">
          <div class="field f-date">
            <label for="timestamp">Date &amp; Time *</label>
            <input id="timestamp" v-model="formData.timestamp" type="datetime-local" required />
          </div>
          <div class="field f-type">
            <label for="type">Type *</label>
            <select id="type" v-model="formData.type" required>
              <option value="BUY">BUY</option>
              <option value="SELL">SELL</option>
              <option value="DEPOSIT">DEPOSIT</option>
              <option value="WITHDRAW">WITHDRAW</option>
            </select>
          </div>
          <template v-if="formData.type === 'BUY' || formData.type === 'SELL'">
            <div class="field f-symbol">
              <label for="symbol">Symbol *</label>
              <input id="symbol" v-model="formData.symbol" type="text" placeholder="AAPL" autocapitalize="characters" required />
            </div>
            <div class="field f-num">
              <label for="qty">Quantity *</label>
              <input id="qty" v-model.number="formData.qty" type="number" step="0.01" min="0" placeholder="10" required />
            </div>
            <div class="field f-num">
              <label for="price">Price *</label>
              <input id="price" v-model.number="formData.price" type="number" step="0.01" min="0" placeholder="150.00" required />
            </div>
          </template>
          <div class="field f-num">
            <label for="fee">Fee</label>
            <input id="fee" v-model.number="formData.fee" type="number" step="0.01" min="0" placeholder="0.00" />
          </div>
          <div class="field f-note">
            <label for="note">Note</label>
            <input id="note" v-model="formData.note" type="text" placeholder="Optional notes..." />
          </div>
          <div class="entry-actions">
            <button type="submit" class="btn btn--primary" :disabled="transactionStore.isLoading">
              {{ transactionStore.isLoading ? 'Adding...' : 'Add Transaction' }}
            </button>
            <button type="button" class="btn btn--quiet" @click="resetForm">Reset</button>
          </div>
          <p v-if="transactionStore.error" class="error-message" role="alert">
            {{ transactionStore.error }}
          </p>
        </form>
      </section>

      <!-- Ledger -->
      <section class="block" aria-labelledby="ledger-heading">
        <div class="block-head">
          <h2 id="ledger-heading">All Transactions ({{ transactionStore.transactions.length }})</h2>
        </div>

        <div class="filter-bar">
          <div class="search">
            <Search class="search-icon" aria-hidden="true" />
            <label for="tx-search" class="sr-only">Search symbol or note</label>
            <input
              id="tx-search"
              v-model="searchQuery"
              class="input"
              type="search"
              placeholder="Search symbol or note"
              autocomplete="off"
            />
          </div>
          <div class="type-seg" role="group" aria-label="Filter by type">
            <button
              v-for="option in typeOptions"
              :key="option"
              type="button"
              class="seg"
              :aria-pressed="filterType === option"
              @click="filterType = option"
            >
              {{ option === 'ALL' ? 'All Types' : option }}
              <span class="seg-count fig">{{ typeCounts[option] }}</span>
            </button>
          </div>
        </div>

        <p v-if="isFiltered" class="filter-status" aria-live="polite">
          Showing <span class="fig">{{ filteredTransactions.length }}</span> of
          <span class="fig">{{ transactionStore.transactions.length }}</span>
          <button type="button" class="btn btn--quiet btn--sm" @click="clearFilters">Clear filters</button>
        </p>

        <EmptySheet v-if="transactionStore.transactions.length === 0">
          No transactions found. Add your first transaction above!
          <template #action>
            <button type="button" class="btn btn--primary" @click="showAddForm = true">
              <Plus aria-hidden="true" />
              Add Transaction
            </button>
          </template>
        </EmptySheet>

        <EmptySheet v-else-if="filteredTransactions.length === 0" compact>
          No transactions match these filters.
          <template #action>
            <button type="button" class="btn btn--sm" @click="clearFilters">Clear filters</button>
          </template>
        </EmptySheet>

        <div v-else class="ledger-scroll">
          <table class="transactions-table">
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">Type</th>
                <th scope="col">Symbol</th>
                <th scope="col" class="num">Quantity</th>
                <th scope="col" class="num">Price</th>
                <th scope="col" class="num">Fee</th>
                <th scope="col" class="num">Total</th>
                <th scope="col">Note</th>
                <th scope="col"><span class="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tx in filteredTransactions" :key="tx.id">
                <td class="tx-date fig">{{ formatDate(tx.timestamp) }}</td>
                <td>
                  <span class="tx-type" :class="`type-${tx.type.toLowerCase()}`">{{ tx.type }}</span>
                </td>
                <td class="tx-symbol">{{ tx.symbol || '-' }}</td>
                <td class="num">{{ tx.qty !== undefined ? tx.qty.toFixed(2) : '-' }}</td>
                <td class="num">{{ tx.price !== undefined ? `$${tx.price.toFixed(2)}` : '-' }}</td>
                <td class="num">${{ tx.fee.toFixed(2) }}</td>
                <td class="num">
                  {{
                    tx.qty !== undefined && tx.price !== undefined
                      ? `$${(tx.qty * tx.price + tx.fee).toFixed(2)}`
                      : '-'
                  }}
                </td>
                <td class="tx-note">{{ tx.note || '-' }}</td>
                <td class="tx-actions">
                  <button
                    type="button"
                    class="icon-btn"
                    :aria-label="`Delete ${tx.type} ${tx.symbol ?? ''} ${formatDate(tx.timestamp)}`"
                    title="Delete"
                    @click="handleDelete(tx.id)"
                  >
                    <Trash2 aria-hidden="true" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Plus, X, Upload, Search, Trash2 } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/authStore'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { useAnalyticsStore } from '@/stores/analyticsStore'
import { useToast } from '@/composables/useToast'
import TitleBlock from '@/components/pad/TitleBlock.vue'
import EmptySheet from '@/components/pad/EmptySheet.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { CreateTransactionRequest } from '@/services/api/schemas'
import {
  TRANSACTION_TYPES,
  filterTransactions,
  countByType,
  filterFromQuery,
  filterToQuery,
  type TypeFilter
} from '@/utils/transactionFilter'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const transactionStore = useTransactionStore()
const analyticsStore = useAnalyticsStore()
const toast = useToast()

const isLoading = ref(false)
const showAddForm = ref(false)
// 필터 상태는 URL 쿼리(?type=&q=)에 보존되어 새로고침/공유 시 유지된다
const initialFilter = filterFromQuery(route.query)
const filterType = ref<TypeFilter>(initialFilter.type)
const searchQuery = ref(initialFilter.query)
const typeOptions: TypeFilter[] = ['ALL', ...TRANSACTION_TYPES]
const isImporting = ref(false)
const importProgress = ref(0)
const fileInputRef = ref<HTMLInputElement | null>(null)

// 폼 데이터
const formData = reactive<{
  type: 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW'
  symbol: string
  qty: number | undefined
  price: number | undefined
  fee: number
  timestamp: string
  note: string
}>({
  type: 'BUY',
  symbol: '',
  qty: undefined,
  price: undefined,
  fee: 0,
  timestamp: new Date().toISOString().slice(0, 16),
  note: ''
})

// Computed
const filteredTransactions = computed(() =>
  filterTransactions(transactionStore.sortedTransactions, { type: filterType.value, query: searchQuery.value })
)

const typeCounts = computed(() => countByType(transactionStore.transactions))

const isFiltered = computed(() => filterType.value !== 'ALL' || searchQuery.value.trim() !== '')

function clearFilters() {
  filterType.value = 'ALL'
  searchQuery.value = ''
}

watch([filterType, searchQuery], () => {
  router.replace({ query: filterToQuery({ type: filterType.value, query: searchQuery.value }) })
})

// 데이터 로드
async function loadData() {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return
  }

  if (!portfolioStore.currentPortfolio) {
    // 포트폴리오 목록 조회
    await portfolioStore.fetchPortfolios()
  }

  if (portfolioStore.currentPortfolio) {
    isLoading.value = true
    try {
      await transactionStore.fetchTransactions(portfolioStore.currentPortfolio.id)
      await analyticsStore.fetchAnalytics(portfolioStore.currentPortfolio.id)
    } catch (error) {
      console.error('Failed to load transactions:', error)
    } finally {
      isLoading.value = false
    }
  }
}

// 거래 추가
async function handleAddTransaction() {
  if (!portfolioStore.currentPortfolio) {
    return
  }

  const request: CreateTransactionRequest = {
    portfolioId: portfolioStore.currentPortfolio!.id,
    type: formData.type,
    symbol: formData.symbol || undefined,
    qty: formData.qty,
    price: formData.price,
    fee: formData.fee,
    timestamp: new Date(formData.timestamp).toISOString(),
    note: formData.note || undefined
  }

  const success = await transactionStore.createTransaction(request)

  if (success) {
    resetForm()
    showAddForm.value = false
    await analyticsStore.fetchAnalytics(portfolioStore.currentPortfolio!.id)
  }
}

// 거래 삭제
async function handleDelete(id: number) {
  if (confirm('Are you sure you want to delete this transaction?')) {
    const success = await transactionStore.deleteTransaction(id)
    if (success && portfolioStore.currentPortfolio) {
      await analyticsStore.fetchAnalytics(portfolioStore.currentPortfolio.id)
    }
  }
}

function triggerCsvImport() {
  fileInputRef.value?.click()
}

async function handleCsvFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !portfolioStore.currentPortfolio) {
    return
  }

  isImporting.value = true
  importProgress.value = 0

  const worker = new Worker(new URL('../workers/csvImportWorker.ts', import.meta.url), {
    type: 'module'
  })

  worker.onmessage = async (message: MessageEvent<any>) => {
    if (message.data.type === 'progress' && typeof message.data.progress === 'number') {
      importProgress.value = message.data.progress
      return
    }

    if (message.data.type === 'success' && Array.isArray(message.data.data)) {
      const parsed = message.data.data as Array<Record<string, unknown>>
      let created = 0
      for (const row of parsed) {
        const request: CreateTransactionRequest = {
          portfolioId: portfolioStore.currentPortfolio!.id,
          type: (row.type as CreateTransactionRequest['type']) ?? 'BUY',
          symbol: (row.symbol as string | undefined) || undefined,
          qty: typeof row.qty === 'number' ? row.qty : undefined,
          price: typeof row.price === 'number' ? row.price : undefined,
          fee: typeof row.fee === 'number' ? row.fee : 0,
          timestamp:
            typeof row.timestamp === 'string'
              ? new Date(row.timestamp).toISOString()
              : new Date().toISOString(),
          note: (row.note as string | undefined) || undefined
        }

        const success = await transactionStore.createTransaction(request)
        if (success) {
          created += 1
        }
      }

      if (portfolioStore.currentPortfolio) {
        await analyticsStore.fetchAnalytics(portfolioStore.currentPortfolio.id)
      }
      toast.success(`${created} transactions imported.`)
    }

    if (message.data.type === 'error') {
      toast.error(message.data.error ?? 'CSV import failed')
    }

    cleanupWorker(worker)
  }

  worker.onerror = (error) => {
    toast.error(error.message)
    cleanupWorker(worker)
  }

  worker.postMessage({ type: 'parse', file })
}

function cleanupWorker(worker: Worker) {
  worker.terminate()
  isImporting.value = false
  importProgress.value = 0
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

// 폼 리셋
function resetForm() {
  formData.type = 'BUY'
  formData.symbol = ''
  formData.qty = undefined
  formData.price = undefined
  formData.fee = 0
  formData.timestamp = new Date().toISOString().slice(0, 16)
  formData.note = ''
  transactionStore.clearError()
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

// 초기 로드
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.entry {
  margin-bottom: var(--space-12);
}

.entry-line {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
  gap: var(--space-4);
  align-items: end;
  padding-bottom: var(--space-5);
  border-bottom: 3px double var(--rule-strong);
}

.f-date {
  min-width: 13rem;
}

.f-note {
  grid-column: span 2;
}

.entry-actions {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.error-message {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--red-pencil);
  font-size: var(--text-sm);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  align-items: center;
  margin-bottom: var(--space-4);
}

.search {
  position: relative;
  flex: 1 1 16rem;
  max-width: 24rem;
}

.search-icon {
  position: absolute;
  left: var(--space-3);
  top: 50%;
  width: 18px;
  height: 18px;
  transform: translateY(-50%);
  color: var(--ink-2);
  pointer-events: none;
}

.search .input {
  padding-left: var(--space-10);
}

.type-seg {
  display: inline-flex;
  flex-wrap: wrap;
  border: 1.5px solid var(--rule-strong);
}

.seg {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 44px;
  padding: 0 var(--space-4);
  font-family: var(--font-label);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--ink);
}

.seg + .seg {
  border-left: 1px solid var(--rule);
}

.seg:hover {
  background-color: var(--paper-sunk);
}

.seg[aria-pressed='true'] {
  background-color: var(--ink);
  color: var(--paper);
}

.seg-count {
  font-size: var(--text-xs);
  opacity: 0.8;
}

.filter-status {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  color: var(--ink-2);
}

.transactions-table td {
  font-size: var(--text-sm);
}

.tx-date {
  white-space: nowrap;
  color: var(--ink-2);
}

.tx-type {
  font-family: var(--font-label);
  font-weight: 700;
  letter-spacing: var(--tracking-label);
}

.type-sell {
  color: var(--red-pencil);
}

.type-deposit,
.type-withdraw {
  color: var(--blue-pencil);
}

.tx-symbol {
  font-weight: 600;
}

.tx-note {
  max-width: 18rem;
  color: var(--ink-2);
}

.tx-actions {
  width: 1%;
  text-align: right;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--ink-2);
  border: 1px solid transparent;
}

.icon-btn:hover {
  color: var(--red-pencil);
  border-color: var(--red-pencil);
}

.icon-btn svg {
  width: 18px;
  height: 18px;
}

@media (max-width: 720px) {
  .f-note {
    grid-column: 1 / -1;
  }

  .entry-line {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .f-date {
    grid-column: 1 / -1;
    min-width: 0;
  }

  .entry-actions {
    grid-column: 1 / -1;
  }

  .search {
    max-width: none;
  }

  .type-seg {
    width: 100%;
  }

  .seg {
    flex: 1 1 auto;
    justify-content: center;
    padding: 0 var(--space-2);
  }
}
</style>
