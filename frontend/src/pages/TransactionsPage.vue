<template>
  <div class="page transactions-page">
    <TitleBlock :title="t('transactions.title')" sheet="02 / 04">
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
          {{ showAddForm ? t('common.cancel') : t('transactions.addTransaction') }}
        </button>
        <button type="button" class="btn" :disabled="isImporting" @click="triggerCsvImport">
          <Upload aria-hidden="true" />
          {{ isImporting ? t('transactions.importing', { progress: importProgress }) : t('transactions.importCsv') }}
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

    <LoadingSpinner v-if="isLoading" fullscreen :message="t('transactions.loading')" />

    <EmptySheet v-else-if="!authStore.isAuthenticated">
      {{ t('transactions.pleaseLogin') }}
      <template #action>
        <router-link to="/login" class="btn btn--primary">{{ t('common.goToLogin') }}</router-link>
      </template>
    </EmptySheet>

    <EmptySheet v-else-if="!portfolioStore.currentPortfolio">
      {{ t('transactions.noPortfolio') }}
      <template #action>
        <router-link to="/dashboard" class="btn btn--primary">{{ t('common.goToDashboard') }}</router-link>
      </template>
    </EmptySheet>

    <div v-else class="transactions-content">
      <aside class="beginner-banner" role="note">
        <p>{{ t('transactions.beginnerTip') }}</p>
      </aside>

      <!-- New entry: the ledger's next blank line -->
      <section v-if="showAddForm" id="tx-entry" class="block entry" aria-labelledby="entry-heading">
        <div class="block-head">
          <h2 id="entry-heading">{{ t('transactions.newHeading') }}</h2>
          <p>{{ t('transactions.requiredHint') }}</p>
        </div>
        <form class="entry-line" @submit.prevent="handleAddTransaction">
          <div class="field f-date">
            <label for="timestamp">{{ t('transactions.dateTime') }}</label>
            <input id="timestamp" v-model="formData.timestamp" type="datetime-local" required />
          </div>
          <div class="field f-type">
            <label for="type">{{ t('transactions.typeRequired') }}</label>
            <select id="type" v-model="formData.type" required>
              <option v-for="type in TRANSACTION_TYPES" :key="type" :value="type">{{ transactionTypeLabel(type) }}</option>
            </select>
          </div>
          <template v-if="formData.type === 'BUY' || formData.type === 'SELL'">
            <div class="field f-symbol">
              <label for="symbol">{{ t('transactions.symbolRequired') }}</label>
              <input id="symbol" v-model="formData.symbol" type="text" placeholder="AAPL" autocapitalize="characters" required />
            </div>
            <div class="field f-num">
              <label for="qty">{{ t('transactions.quantityRequired') }}</label>
              <input id="qty" v-model.number="formData.qty" type="number" step="0.01" min="0" placeholder="10" required />
            </div>
            <div class="field f-num">
              <label for="price">{{ t('transactions.priceRequired') }}</label>
              <input id="price" v-model.number="formData.price" type="number" step="0.01" min="0" placeholder="150.00" required />
            </div>
          </template>
          <div class="field f-num">
            <label for="fee">{{ t('transactions.colFee') }}</label>
            <input id="fee" v-model.number="formData.fee" type="number" step="0.01" min="0" placeholder="0.00" />
          </div>
          <div class="field f-note">
            <label for="note">{{ t('transactions.colNote') }}</label>
            <input id="note" v-model="formData.note" type="text" :placeholder="t('transactions.notePlaceholder')" />
          </div>
          <div class="entry-actions">
            <button type="submit" class="btn btn--primary" :disabled="transactionStore.isLoading">
              {{ transactionStore.isLoading ? t('transactions.adding') : t('transactions.addTransaction') }}
            </button>
            <button type="button" class="btn btn--quiet" @click="resetForm">{{ t('common.reset') }}</button>
          </div>
          <p v-if="createError" class="error-message" role="alert">
            {{ createError }}
          </p>
        </form>
      </section>

      <!-- Ledger -->
      <section class="block" aria-labelledby="ledger-heading">
        <div class="block-head">
          <h2 id="ledger-heading">{{ t('transactions.allTransactions', { count: transactionStore.transactions.length }) }}</h2>
        </div>

        <div class="filter-bar">
          <div class="search">
            <Search class="search-icon" aria-hidden="true" />
            <label for="tx-search" class="sr-only">{{ t('transactions.searchLabel') }}</label>
            <input
              id="tx-search"
              v-model="searchQuery"
              class="input"
              type="search"
              :placeholder="t('transactions.searchLabel')"
              autocomplete="off"
            />
          </div>
          <div class="type-seg" role="group" :aria-label="t('transactions.filterByType')">
            <button
              v-for="option in typeOptions"
              :key="option"
              type="button"
              class="seg"
              :aria-pressed="filterType === option"
              @click="filterType = option"
            >
              {{ option === 'ALL' ? t('transactions.allTypes') : transactionTypeLabel(option) }}
              <span class="seg-count fig">{{ typeCounts[option] }}</span>
            </button>
          </div>
        </div>

        <p v-if="isFiltered" class="filter-status" aria-live="polite">
          <i18n-t scope="global" keypath="transactions.showing" tag="span">
            <template #shown><span class="fig">{{ filteredTransactions.length }}</span></template>
            <template #total><span class="fig">{{ transactionStore.transactions.length }}</span></template>
          </i18n-t>
          <button type="button" class="btn btn--quiet btn--sm" @click="clearFilters">{{ t('transactions.clearFilters') }}</button>
        </p>

        <EmptySheet v-if="transactionStore.transactions.length === 0">
          {{ t('transactions.empty') }}
          <template #action>
            <button type="button" class="btn btn--primary" @click="showAddForm = true">
              <Plus aria-hidden="true" />
              {{ t('transactions.addTransaction') }}
            </button>
          </template>
        </EmptySheet>

        <EmptySheet v-else-if="filteredTransactions.length === 0" compact>
          {{ t('transactions.noMatch') }}
          <template #action>
            <button type="button" class="btn btn--sm" @click="clearFilters">{{ t('transactions.clearFilters') }}</button>
          </template>
        </EmptySheet>

        <div v-else class="ledger-scroll">
          <table class="transactions-table">
            <thead>
              <tr>
                <th scope="col">{{ t('transactions.colDate') }}</th>
                <th scope="col">{{ t('transactions.colType') }}</th>
                <th scope="col">{{ t('transactions.colSymbol') }}</th>
                <th scope="col" class="num">{{ t('transactions.colQuantity') }}</th>
                <th scope="col" class="num">{{ t('transactions.colPrice') }}</th>
                <th scope="col" class="num">{{ t('transactions.colFee') }}</th>
                <th scope="col" class="num">{{ t('transactions.colTotal') }}</th>
                <th scope="col">{{ t('transactions.colNote') }}</th>
                <th scope="col"><span class="sr-only">{{ t('transactions.colActions') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tx in filteredTransactions" :key="tx.id">
                <td :data-label="t('transactions.colDate')" class="tx-date fig">{{ formatDateTime(tx.timestamp, locale) }}</td>
                <td :data-label="t('transactions.colType')">
                  <span class="tx-type" :class="`type-${tx.type.toLowerCase()}`">{{ transactionTypeLabel(tx.type) }}</span>
                </td>
                <td :data-label="t('transactions.colSymbol')" class="tx-symbol">{{ tx.symbol || '-' }}</td>
                <td :data-label="t('transactions.colQuantity')" class="num">{{ tx.qty !== undefined ? formatNumber(tx.qty, locale) : '-' }}</td>
                <td :data-label="t('transactions.colPrice')" class="num">{{ tx.price !== undefined ? formatUsd(tx.price, locale) : '-' }}</td>
                <td :data-label="t('transactions.colFee')" class="num">{{ formatUsd(tx.fee, locale) }}</td>
                <td :data-label="t('transactions.colTotal')" class="num">
                  {{
                    tx.qty !== undefined && tx.price !== undefined
                      ? formatUsd(tx.qty * tx.price + tx.fee, locale)
                      : '-'
                  }}
                </td>
                <td :data-label="t('transactions.colNote')" class="tx-note">{{ tx.note || '-' }}</td>
                <td :data-label="t('transactions.colActions')" class="tx-actions">
                  <button
                    type="button"
                    class="icon-btn"
                    :aria-label="
                      t('transactions.deleteLabel', {
                        type: transactionTypeLabel(tx.type),
                        symbol: tx.symbol ?? '',
                        date: formatDateTime(tx.timestamp, locale)
                      })
                    "
                    :title="t('common.delete')"
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
import { useI18n } from 'vue-i18n'
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
import { toDateTimeLocalValue } from '@/utils/dateInput'
import { importTransactions, toImportRequests, type InvalidCsvRow } from '@/utils/csvImport'
import { formatDateTime, formatNumber, formatUsd } from '@/utils/localeFormat'
import { failureMessage, summarizeImportLocalized, transactionTypeLabel } from '@/utils/localizedMessages'

const router = useRouter()
const route = useRoute()
const { t, locale } = useI18n()
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

// 스토어 오류 원문은 그대로 두고 표시할 때 현재 언어 제목과 함께 보여준다
const createError = computed(() =>
  transactionStore.error ? failureMessage('transactions.createFailed', transactionStore.error) : ''
)

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
  timestamp: toDateTimeLocalValue(),
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
      if (transactionStore.error) {
        toast.error(failureMessage('transactions.loadFailed', transactionStore.error))
      }
      await analyticsStore.fetchAnalytics(portfolioStore.currentPortfolio.id)
    } catch (error) {
      console.error('Failed to load transactions:', error)
      toast.error(failureMessage('transactions.loadFailed', error instanceof Error ? error.message : null))
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
  if (confirm(t('transactions.confirmDelete'))) {
    const success = await transactionStore.deleteTransaction(id)
    if (!success) {
      // 폼의 오류 줄은 입력 폼이 열려 있을 때만 보이므로 삭제 거부는 toast로 알린다
      toast.error(failureMessage('transactions.deleteFailed', transactionStore.error))
      transactionStore.clearError()
      return
    }
    if (portfolioStore.currentPortfolio) {
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
      const invalid = (Array.isArray(message.data.invalid) ? message.data.invalid : []) as InvalidCsvRow[]
      // 최신순 CSV도 FIFO 매도 검증을 통과하도록 시간순으로 저장하고, 거부된 행은 따로 알린다
      const { created, rejected } = await importTransactions(
        toImportRequests(parsed, portfolioStore.currentPortfolio!.id),
        async (request) =>
          (await transactionStore.createTransaction(request))
            ? null
            : (transactionStore.error ?? t('transactions.createFailed'))
      )

      if (portfolioStore.currentPortfolio) {
        await analyticsStore.fetchAnalytics(portfolioStore.currentPortfolio.id)
      }
      // 파싱 거부와 저장 거부를 함께 보여주고, 하나라도 있으면 성공으로 알리지 않는다
      const summary = summarizeImportLocalized(created, invalid, rejected)
      if (summary.complete) {
        toast.success(summary.message)
      } else {
        toast.error(summary.message)
      }
    }

    if (message.data.type === 'error') {
      toast.error(failureMessage('transactions.importFailed', message.data.error))
    }

    cleanupWorker(worker)
  }

  worker.onerror = (error) => {
    toast.error(failureMessage('transactions.importFailed', error.message))
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
  formData.timestamp = toDateTimeLocalValue()
  formData.note = ''
  transactionStore.clearError()
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
  font-weight: 600;
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
  font-size: var(--text-xs);
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

@media (max-width: 680px) {
  .transactions-table thead {
    display: none;
  }

  .transactions-table,
  .transactions-table tbody {
    display: block;
    width: 100%;
  }

  .transactions-table tr {
    display: block;
    border: 1px solid var(--rule-strong);
    background-color: var(--paper-raised);
    margin-bottom: var(--space-4);
    padding: var(--space-3) var(--space-4);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  }

  .transactions-table tr:hover {
    background-color: var(--paper-raised);
  }

  .transactions-table td {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: var(--space-2) 0;
    border-bottom: 1px dashed var(--rule);
    text-align: right;
  }

  .transactions-table td:last-child {
    border-bottom: none;
  }

  .transactions-table td::before {
    content: attr(data-label);
    font-family: var(--font-label);
    font-size: var(--text-xs);
    font-weight: 600;
    color: var(--ink-2);
    text-align: left;
    margin-right: var(--space-3);
  }
}
</style>
