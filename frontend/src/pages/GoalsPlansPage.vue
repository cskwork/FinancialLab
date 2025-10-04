<template>
  <div class="goals-plans-page">
    <div class="page-header">
      <h1>Goals & Plans</h1>
      <p class="page-subtitle">Track long-term objectives and automated DCA investment plans.</p>
    </div>

    <div v-if="!authStore.isAuthenticated" class="empty-state">
      <p>로그인 후 목표와 계획을 관리할 수 있습니다.</p>
      <router-link to="/login" class="btn-primary">Login</router-link>
    </div>

    <div v-else-if="!portfolioStore.currentPortfolio" class="empty-state">
      <p>먼저 포트폴리오를 생성하거나 선택하세요.</p>
      <router-link to="/dashboard" class="btn-primary">Go to Dashboard</router-link>
    </div>

    <div v-else class="content-grid">
      <section class="panel">
        <header class="panel-header">
          <div>
            <h2>Financial Goals</h2>
            <p class="panel-description">Set measurable targets for your portfolio growth.</p>
          </div>
          <button type="button" class="btn-secondary" @click="toggleGoalForm">
            {{ showGoalForm ? '취소' : '+ Add Goal' }}
          </button>
        </header>

        <Card v-if="showGoalForm">
          <form class="form-grid" @submit.prevent="handleCreateGoal">
            <label>
              Goal Name
              <input v-model="goalForm.name" type="text" required placeholder="Retirement Fund" />
            </label>
            <label>
              Target Amount (USD)
              <input v-model.number="goalForm.targetAmount" type="number" min="1" step="100" required />
            </label>
            <label>
              Current Amount
              <input v-model.number="goalForm.currentAmount" type="number" min="0" step="100" />
            </label>
            <label>
              Target Date
              <input v-model="goalForm.targetDate" type="date" required />
            </label>
            <div class="form-actions">
              <button type="submit" class="btn-primary" :disabled="goalPlanStore.isLoadingGoals">
                {{ goalPlanStore.isLoadingGoals ? 'Saving...' : 'Save Goal' }}
              </button>
              <button type="button" class="btn-tertiary" @click="resetGoalForm">Reset</button>
            </div>
          </form>
        </Card>

        <div v-if="goalPlanStore.isLoadingGoals" class="loading-note">Loading goals...</div>
        <ul v-else-if="goals.length > 0" class="item-list">
          <li v-for="goal in goals" :key="goal.id" class="item-card">
            <div>
              <h3>{{ goal.name }}</h3>
              <dl class="item-meta">
                <div>
                  <dt>Target Amount</dt>
                  <dd>${{ formatCurrency(goal.targetAmount) }}</dd>
                </div>
                <div>
                  <dt>Current Amount</dt>
                  <dd>${{ formatCurrency(goal.currentAmount) }}</dd>
                </div>
                <div>
                  <dt>Target Date</dt>
                  <dd>{{ formatDate(goal.targetDate) }}</dd>
                </div>
              </dl>
            </div>
            <button class="btn-danger" type="button" @click="handleDeleteGoal(goal.id)">Delete</button>
          </li>
        </ul>
        <div v-else class="empty-note">아직 등록된 목표가 없습니다.</div>
      </section>

      <section class="panel">
        <header class="panel-header">
          <div>
            <h2>Recurring Plans</h2>
            <p class="panel-description">Automate dollar-cost averaging with recurring buy plans.</p>
          </div>
          <button type="button" class="btn-secondary" @click="togglePlanForm">
            {{ showPlanForm ? '취소' : '+ Add Plan' }}
          </button>
        </header>

        <Card v-if="showPlanForm">
          <form class="form-grid" @submit.prevent="handleCreatePlan">
            <label>
              Symbol
              <input v-model="planForm.symbol" type="text" required placeholder="AAPL" />
            </label>
            <label>
              Amount per Interval (USD)
              <input v-model.number="planForm.amountPerInterval" type="number" min="1" step="10" required />
            </label>
            <label>
              Interval
              <select v-model="planForm.interval" required>
                <option value="WEEKLY">Weekly</option>
                <option value="MONTHLY">Monthly</option>
              </select>
            </label>
            <label>
              Start Date
              <input v-model="planForm.startDate" type="date" required />
            </label>
            <label>
              End Date (Optional)
              <input v-model="planForm.endDate" type="date" />
            </label>
            <label class="checkbox-field">
              <input v-model="planForm.isActive" type="checkbox" />
              Active
            </label>
            <div class="form-actions">
              <button type="submit" class="btn-primary" :disabled="goalPlanStore.isLoadingPlans">
                {{ goalPlanStore.isLoadingPlans ? 'Saving...' : 'Save Plan' }}
              </button>
              <button type="button" class="btn-tertiary" @click="resetPlanForm">Reset</button>
            </div>
          </form>
        </Card>

        <div v-if="goalPlanStore.isLoadingPlans" class="loading-note">Loading plans...</div>
        <ul v-else-if="plans.length > 0" class="item-list">
          <li v-for="plan in plans" :key="plan.id" class="item-card">
            <div>
              <h3>{{ plan.symbol }}</h3>
              <dl class="item-meta">
                <div>
                  <dt>Amount</dt>
                  <dd>${{ formatCurrency(plan.amountPerInterval) }}</dd>
                </div>
                <div>
                  <dt>Interval</dt>
                  <dd>{{ plan.interval }}</dd>
                </div>
                <div>
                  <dt>Duration</dt>
                  <dd>{{ formatPlanDuration(plan.startDate, plan.endDate) }}</dd>
                </div>
              </dl>
            </div>
            <button class="btn-danger" type="button" @click="handleDeletePlan(plan.id)">Delete</button>
          </li>
        </ul>
        <div v-else class="empty-note">등록된 투자 계획이 없습니다.</div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/common/Card.vue'
import { useAuthStore } from '@/stores/authStore'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { useGoalPlanStore } from '@/stores/goalPlanStore'
import { useToast } from '@/composables/useToast'

const router = useRouter()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const goalPlanStore = useGoalPlanStore()
const toast = useToast()

const showGoalForm = ref(false)
const showPlanForm = ref(false)

const goals = computed(() => goalPlanStore.goals)
const plans = computed(() => goalPlanStore.plans)

const goalForm = reactive({
  name: '',
  targetAmount: 10000,
  currentAmount: 0,
  targetDate: new Date().toISOString().slice(0, 10)
})

const planForm = reactive({
  symbol: 'AAPL',
  amountPerInterval: 500,
  interval: 'MONTHLY' as 'WEEKLY' | 'MONTHLY',
  startDate: new Date().toISOString().slice(0, 10),
  endDate: '',
  isActive: true
})

function ensurePortfolio() {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return false
  }
  if (!portfolioStore.currentPortfolio) {
    router.push('/dashboard')
    return false
  }
  return true
}

async function loadData() {
  if (!ensurePortfolio()) return
  const portfolioId = portfolioStore.currentPortfolio!.id
  await Promise.all([
    goalPlanStore.fetchGoals(portfolioId),
    goalPlanStore.fetchPlans(portfolioId)
  ])
}

function toggleGoalForm() {
  showGoalForm.value = !showGoalForm.value
}

function togglePlanForm() {
  showPlanForm.value = !showPlanForm.value
}

function resetGoalForm() {
  goalForm.name = ''
  goalForm.targetAmount = 10000
  goalForm.currentAmount = 0
  goalForm.targetDate = new Date().toISOString().slice(0, 10)
  goalPlanStore.clearError()
}

function resetPlanForm() {
  planForm.symbol = 'AAPL'
  planForm.amountPerInterval = 500
  planForm.interval = 'MONTHLY'
  planForm.startDate = new Date().toISOString().slice(0, 10)
  planForm.endDate = ''
  planForm.isActive = true
  goalPlanStore.clearError()
}

async function handleCreateGoal() {
  if (!portfolioStore.currentPortfolio) return
  const success = await goalPlanStore.createGoal({
    portfolioId: portfolioStore.currentPortfolio.id,
    name: goalForm.name,
    targetAmount: goalForm.targetAmount,
    targetDate: new Date(goalForm.targetDate).toISOString(),
    currentAmount: goalForm.currentAmount
  })

  if (success) {
    toast.success('목표를 저장했습니다.')
    resetGoalForm()
    showGoalForm.value = false
  } else if (goalPlanStore.error) {
    toast.error(goalPlanStore.error)
  }
}

async function handleDeleteGoal(goalId: number) {
  if (!confirm('해당 목표를 삭제하시겠습니까?')) return
  const success = await goalPlanStore.deleteGoal(goalId)
  if (success) {
    toast.warning('목표를 삭제했습니다.')
  } else if (goalPlanStore.error) {
    toast.error(goalPlanStore.error)
  }
}

async function handleCreatePlan() {
  if (!portfolioStore.currentPortfolio) return
  const success = await goalPlanStore.createPlan({
    portfolioId: portfolioStore.currentPortfolio.id,
    symbol: planForm.symbol.toUpperCase(),
    amountPerInterval: planForm.amountPerInterval,
    interval: planForm.interval,
    startDate: new Date(planForm.startDate).toISOString(),
    endDate: planForm.endDate ? new Date(planForm.endDate).toISOString() : undefined,
    isActive: planForm.isActive
  })

  if (success) {
    toast.success('투자 계획을 저장했습니다.')
    resetPlanForm()
    showPlanForm.value = false
  } else if (goalPlanStore.error) {
    toast.error(goalPlanStore.error)
  }
}

async function handleDeletePlan(planId: number) {
  if (!confirm('해당 투자 계획을 삭제하시겠습니까?')) return
  const success = await goalPlanStore.deletePlan(planId)
  if (success) {
    toast.warning('투자 계획을 삭제했습니다.')
  } else if (goalPlanStore.error) {
    toast.error(goalPlanStore.error)
  }
}

function formatCurrency(value: number) {
  return value.toLocaleString('en-US', { maximumFractionDigits: 0 })
}

function formatDate(date: string) {
  return new Date(date).toLocaleDateString()
}

function formatPlanDuration(start: string, end?: string) {
  if (!end) {
    return `${formatDate(start)} → ongoing`
  }
  return `${formatDate(start)} → ${formatDate(end)}`
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.goals-plans-page {
  min-height: 100vh;
  padding: var(--space-6);
  background-color: var(--color-gray-50);
}

.page-header {
  max-width: 960px;
  margin: 0 auto var(--space-8);
}

.page-header h1 {
  margin: 0;
  font-size: var(--font-size-3xl);
}

.page-subtitle {
  margin-top: var(--space-2);
  color: var(--color-text-muted);
}

.empty-state {
  max-width: 640px;
  margin: var(--space-12) auto;
  padding: var(--space-10);
  background-color: var(--color-background);
  border-radius: var(--border-radius-2xl);
  text-align: center;
  box-shadow: var(--shadow-lg);
}

.content-grid {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  gap: var(--space-6);
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
}

.panel {
  background-color: var(--color-background);
  border-radius: var(--border-radius-2xl);
  box-shadow: var(--shadow-md);
  padding: var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-4);
}

.panel-description {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-4);
}

.form-grid label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
  color: var(--color-text);
}

.form-grid input,
.form-grid select {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
}

.checkbox-field {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  gap: var(--space-3);
}

.btn-primary,
.btn-secondary,
.btn-tertiary,
.btn-danger {
  padding: var(--space-2) var(--space-4);
  border-radius: var(--border-radius-md);
  border: none;
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn-primary {
  background-color: var(--color-primary-600);
  color: #fff;
}

.btn-secondary {
  background-color: var(--color-gray-200);
}

.btn-tertiary {
  background-color: transparent;
  color: var(--color-primary-600);
}

.btn-danger {
  background-color: var(--color-error-600);
  color: #fff;
}

.btn-primary:hover {
  background-color: var(--color-primary-700);
}

.btn-secondary:hover {
  background-color: var(--color-gray-300);
}

.btn-tertiary:hover {
  text-decoration: underline;
}

.btn-danger:hover {
  background-color: var(--color-error-500);
}

.item-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.item-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-xl);
}

.item-card h3 {
  margin: 0 0 var(--space-2);
}

.item-meta {
  display: grid;
  gap: var(--space-2);
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  margin: 0;
}

.item-meta dt {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.item-meta dd {
  margin: 0;
  font-size: var(--font-size-sm);
}

.empty-note {
  padding: var(--space-4);
  text-align: center;
  color: var(--color-text-muted);
  background-color: var(--color-gray-100);
  border-radius: var(--border-radius-lg);
}

.loading-note {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

@media (max-width: 768px) {
  .content-grid {
    grid-template-columns: 1fr;
  }

  .item-card {
    flex-direction: column;
    align-items: stretch;
  }

  .form-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
