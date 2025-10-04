<template>
  <div class="transactions-page">
    <div class="transactions-container">
      <header class="transactions-header">
        <h1>Transactions</h1>
        <div class="header-actions">
          <button @click="showAddForm = !showAddForm" class="btn-primary">
            {{ showAddForm ? 'Cancel' : '+ Add Transaction' }}
          </button>
          <button @click="triggerCsvImport" class="btn-secondary" :disabled="isImporting">
            {{ isImporting ? `Importing ${importProgress}%` : 'Import CSV' }}
          </button>
          <input
            ref="fileInputRef"
            type="file"
            accept=".csv"
            class="visually-hidden"
            @change="handleCsvFile"
          />
        </div>
      </header>

      <LoadingSpinner v-if="isLoading" fullscreen message="Loading transactions..." />

      <div v-else-if="!authStore.isAuthenticated" class="empty-state">
        <p>Please login to view transactions</p>
        <router-link to="/login" class="btn-primary">Go to Login</router-link>
      </div>

      <div v-else-if="!portfolioStore.currentPortfolio" class="empty-state">
        <p>No portfolio selected. Go to dashboard to create one.</p>
        <router-link to="/dashboard" class="btn-primary">Go to Dashboard</router-link>
      </div>

      <div v-else class="transactions-content">
        <!-- Add Transaction Form -->
        <Card v-if="showAddForm" class="add-form">
          <template #header>
            <h2>Add New Transaction</h2>
          </template>
          <form @submit.prevent="handleAddTransaction" class="transaction-form">
            <div class="form-row">
              <div class="form-group">
                <label for="type">Type *</label>
                <select id="type" v-model="formData.type" required>
                  <option value="BUY">BUY</option>
                  <option value="SELL">SELL</option>
                  <option value="DEPOSIT">DEPOSIT</option>
                  <option value="WITHDRAW">WITHDRAW</option>
                </select>
              </div>

              <div class="form-group" v-if="formData.type === 'BUY' || formData.type === 'SELL'">
                <label for="symbol">Symbol *</label>
                <input
                  id="symbol"
                  v-model="formData.symbol"
                  type="text"
                  placeholder="AAPL"
                  required
                />
              </div>

              <div class="form-group" v-if="formData.type === 'BUY' || formData.type === 'SELL'">
                <label for="qty">Quantity *</label>
                <input
                  id="qty"
                  v-model.number="formData.qty"
                  type="number"
                  step="0.01"
                  placeholder="10"
                  required
                />
              </div>

              <div class="form-group" v-if="formData.type === 'BUY' || formData.type === 'SELL'">
                <label for="price">Price *</label>
                <input
                  id="price"
                  v-model.number="formData.price"
                  type="number"
                  step="0.01"
                  placeholder="150.00"
                  required
                />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label for="fee">Fee</label>
                <input
                  id="fee"
                  v-model.number="formData.fee"
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                />
              </div>

              <div class="form-group">
                <label for="timestamp">Date & Time *</label>
                <input id="timestamp" v-model="formData.timestamp" type="datetime-local" required />
              </div>
            </div>

            <div class="form-group">
              <label for="note">Note</label>
              <textarea
                id="note"
                v-model="formData.note"
                placeholder="Optional notes..."
                rows="2"
              ></textarea>
            </div>

            <div class="form-actions">
              <button type="submit" class="btn-primary" :disabled="transactionStore.isLoading">
                {{ transactionStore.isLoading ? 'Adding...' : 'Add Transaction' }}
              </button>
              <button type="button" @click="resetForm" class="btn-secondary">Reset</button>
            </div>

            <p v-if="transactionStore.error" class="error-message">
              {{ transactionStore.error }}
            </p>
          </form>
        </Card>

        <!-- Transactions List -->
        <Card>
          <template #header>
            <div class="list-header">
              <h2>All Transactions ({{ transactionStore.transactions.length }})</h2>
              <div class="filter-actions">
                <select v-model="filterType" class="filter-select">
                  <option value="ALL">All Types</option>
                  <option value="BUY">BUY</option>
                  <option value="SELL">SELL</option>
                  <option value="DEPOSIT">DEPOSIT</option>
                  <option value="WITHDRAW">WITHDRAW</option>
                </select>
              </div>
            </div>
          </template>

          <div v-if="filteredTransactions.length === 0" class="empty-message">
            <p>No transactions found. Add your first transaction above!</p>
          </div>

          <div v-else class="table-container">
            <table class="transactions-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Type</th>
                  <th>Symbol</th>
                  <th>Quantity</th>
                  <th>Price</th>
                  <th>Fee</th>
                  <th>Total</th>
                  <th>Note</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="tx in filteredTransactions"
                  :key="tx.id"
                  :class="`tx-row-${tx.type.toLowerCase()}`"
                >
                  <td class="tx-date">{{ formatDate(tx.timestamp) }}</td>
                  <td>
                    <span class="tx-type-badge" :class="`badge-${tx.type.toLowerCase()}`">
                      {{ tx.type }}
                    </span>
                  </td>
                  <td>{{ tx.symbol || '-' }}</td>
                  <td>{{ tx.qty !== undefined ? tx.qty.toFixed(2) : '-' }}</td>
                  <td>{{ tx.price !== undefined ? `$${tx.price.toFixed(2)}` : '-' }}</td>
                  <td>${{ tx.fee.toFixed(2) }}</td>
                  <td>
                    {{
                      tx.qty !== undefined && tx.price !== undefined
                        ? `$${(tx.qty * tx.price + tx.fee).toFixed(2)}`
                        : '-'
                    }}
                  </td>
                  <td class="tx-note">{{ tx.note || '-' }}</td>
                  <td>
                    <button @click="handleDelete(tx.id)" class="btn-delete" title="Delete">
                      🗑️
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { useAnalyticsStore } from '@/stores/analyticsStore'
import { useToast } from '@/composables/useToast'
import Card from '@/components/common/Card.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { CreateTransactionRequest } from '@/services/api/schemas'

const router = useRouter()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const transactionStore = useTransactionStore()
const analyticsStore = useAnalyticsStore()
const toast = useToast()

const isLoading = ref(false)
const showAddForm = ref(false)
const filterType = ref('ALL')
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
const filteredTransactions = computed(() => {
  if (filterType.value === 'ALL') {
    return transactionStore.sortedTransactions
  }
  return transactionStore.sortedTransactions.filter((tx) => tx.type === filterType.value)
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
.transactions-page {
  min-height: 100vh;
  background-color: var(--color-gray-50);
  padding: var(--space-6);
}

.transactions-container {
  max-width: 1400px;
  margin: 0 auto;
}

.transactions-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
  gap: var(--space-4);
}

.transactions-header h1 {
  margin: 0;
  font-size: var(--font-size-3xl);
  color: var(--color-text);
}

.header-actions {
  display: flex;
  gap: var(--space-3);
}

.btn-primary {
  padding: var(--space-3) var(--space-6);
  background-color: var(--color-primary-600);
  color: white;
  border: none;
  border-radius: var(--border-radius-md);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
  text-decoration: none;
  display: inline-block;
}

.btn-primary:hover:not(:disabled) {
  background-color: var(--color-primary-700);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.btn-primary:disabled {
  background-color: var(--color-gray-400);
  cursor: not-allowed;
  transform: none;
}

.btn-secondary {
  padding: var(--space-3) var(--space-6);
  background-color: var(--color-gray-200);
  color: var(--color-text);
  border: none;
  border-radius: var(--border-radius-md);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn-secondary:hover {
  background-color: var(--color-gray-300);
}

.btn-secondary:disabled {
  background-color: var(--color-gray-300);
  cursor: not-allowed;
}

.btn-delete {
  padding: var(--space-1) var(--space-2);
  background: none;
  border: none;
  cursor: pointer;
  font-size: var(--font-size-lg);
  transition: transform var(--transition-base);
}

.btn-delete:hover {
  transform: scale(1.2);
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
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

.transactions-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.add-form h2 {
  margin: 0;
  font-size: var(--font-size-xl);
}

.transaction-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.form-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-4);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form-group label {
  font-weight: var(--font-weight-medium);
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.form-group input,
.form-group select,
.form-group textarea {
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-base);
  transition: all var(--transition-base);
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--color-primary-500);
  box-shadow: 0 0 0 3px var(--color-primary-100);
}

.form-actions {
  display: flex;
  gap: var(--space-3);
}

.error-message {
  color: var(--color-error-600);
  font-size: var(--font-size-sm);
  padding: var(--space-2);
  background-color: var(--color-error-50);
  border-radius: var(--border-radius-md);
  margin: 0;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-header h2 {
  margin: 0;
  font-size: var(--font-size-xl);
}

.filter-select {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
  background-color: var(--color-background);
  cursor: pointer;
}

.empty-message {
  text-align: center;
  padding: var(--space-8);
  color: var(--color-text-muted);
}

.table-container {
  overflow-x: auto;
}

.transactions-table {
  width: 100%;
  border-collapse: collapse;
}

.transactions-table th {
  text-align: left;
  padding: var(--space-3);
  border-bottom: 2px solid var(--color-border);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  text-transform: uppercase;
}

.transactions-table td {
  padding: var(--space-3);
  border-bottom: 1px solid var(--color-border);
  font-size: var(--font-size-sm);
}

.transactions-table tbody tr:hover {
  background-color: var(--color-gray-50);
}

.tx-date {
  white-space: nowrap;
}

.tx-type-badge {
  display: inline-block;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  text-transform: uppercase;
}

.badge-buy {
  background-color: var(--color-success-100);
  color: var(--color-success-700);
}

.badge-sell {
  background-color: var(--color-error-100);
  color: var(--color-error-700);
}

.badge-deposit,
.badge-withdraw {
  background-color: var(--color-info-100);
  color: var(--color-info-700);
}

.tx-note {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .transactions-page {
    padding: var(--space-4);
  }

  .transactions-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .table-container {
    font-size: var(--font-size-xs);
  }
}
</style>
