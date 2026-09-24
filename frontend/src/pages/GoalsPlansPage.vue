<template>
  <div class="page goals-plans-page">
    <TitleBlock title="Goals & Plans" sheet="03 / 04">
      Track long-term objectives and automated DCA investment plans.
    </TitleBlock>

    <EmptySheet v-if="!authStore.isAuthenticated">
      로그인 후 목표와 계획을 관리할 수 있습니다.
      <template #action>
        <router-link to="/login" class="btn btn--primary">Login</router-link>
      </template>
    </EmptySheet>

    <EmptySheet v-else-if="!portfolioStore.currentPortfolio">
      먼저 포트폴리오를 생성하거나 선택하세요.
      <template #action>
        <router-link to="/dashboard" class="btn btn--primary">Go to Dashboard</router-link>
      </template>
    </EmptySheet>

    <div v-else class="content-grid">
      <section class="panel" aria-labelledby="goals-heading">
        <div class="block-head">
          <div>
            <h2 id="goals-heading">Financial Goals</h2>
            <p>Set measurable targets for your portfolio growth.</p>
          </div>
          <button
            type="button"
            class="btn btn--sm"
            :aria-expanded="showGoalForm"
            aria-controls="goal-form"
            @click="toggleGoalForm"
          >
            <component :is="showGoalForm ? X : Plus" aria-hidden="true" />
            {{ showGoalForm ? '취소' : 'Add Goal' }}
          </button>
        </div>

        <form v-if="showGoalForm" id="goal-form" class="form-grid entry" @submit.prevent="handleCreateGoal">
          <div class="field span-2">
            <label for="goal-name">Goal Name</label>
            <input id="goal-name" v-model="goalForm.name" type="text" required placeholder="Retirement Fund" />
          </div>
          <div class="field">
            <label for="goal-target">Target Amount (USD)</label>
            <input id="goal-target" v-model.number="goalForm.targetAmount" type="number" min="1" step="100" required />
          </div>
          <div class="field">
            <label for="goal-current">Current Amount</label>
            <input id="goal-current" v-model.number="goalForm.currentAmount" type="number" min="0" step="100" />
          </div>
          <div class="field">
            <label for="goal-date">Target Date</label>
            <input id="goal-date" v-model="goalForm.targetDate" type="date" required />
          </div>
          <div class="form-actions">
            <button type="submit" class="btn btn--primary" :disabled="goalPlanStore.isLoadingGoals">
              {{ goalPlanStore.isLoadingGoals ? 'Saving...' : 'Save Goal' }}
            </button>
            <button type="button" class="btn btn--quiet" @click="resetGoalForm">Reset</button>
          </div>
        </form>

        <LoadingSpinner v-if="goalPlanStore.isLoadingGoals" message="Loading goals..." />
        <ul v-else-if="goals.length > 0" class="item-list">
          <li v-for="goal in goals" :key="goal.id" class="item">
            <div class="item-main">
              <div class="item-title">
                <h3>{{ goal.name }}</h3>
                <span class="pct fig">{{ progressOf(goal.currentAmount, goal.targetAmount).toFixed(0) }}%</span>
              </div>
              <div
                class="progress"
                role="progressbar"
                :aria-valuenow="Math.round(progressOf(goal.currentAmount, goal.targetAmount))"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-label="`${goal.name} progress`"
              >
                <span class="progress-fill" :style="{ width: `${progressOf(goal.currentAmount, goal.targetAmount)}%` }"></span>
              </div>
              <dl class="item-meta">
                <div>
                  <dt>Current Amount</dt>
                  <dd class="fig">${{ formatCurrency(goal.currentAmount) }}</dd>
                </div>
                <div>
                  <dt>Target Amount</dt>
                  <dd class="fig">${{ formatCurrency(goal.targetAmount) }}</dd>
                </div>
                <div>
                  <dt>Target Date</dt>
                  <dd class="fig">{{ formatDate(goal.targetDate) }}</dd>
                </div>
              </dl>
            </div>
            <button
              class="icon-btn"
              type="button"
              :aria-label="`Delete goal ${goal.name}`"
              title="Delete"
              @click="handleDeleteGoal(goal.id)"
            >
              <Trash2 aria-hidden="true" />
            </button>
          </li>
        </ul>
        <EmptySheet v-else compact>아직 등록된 목표가 없습니다.</EmptySheet>
      </section>

      <section class="panel" aria-labelledby="plans-heading">
        <div class="block-head">
          <div>
            <h2 id="plans-heading">Recurring Plans</h2>
            <p>Automate dollar-cost averaging with recurring buy plans.</p>
          </div>
          <button
            type="button"
            class="btn btn--sm"
            :aria-expanded="showPlanForm"
            aria-controls="plan-form"
            @click="togglePlanForm"
          >
            <component :is="showPlanForm ? X : Plus" aria-hidden="true" />
            {{ showPlanForm ? '취소' : 'Add Plan' }}
          </button>
        </div>

        <form v-if="showPlanForm" id="plan-form" class="form-grid entry" @submit.prevent="handleCreatePlan">
          <div class="field">
            <label for="plan-symbol">Symbol</label>
            <input id="plan-symbol" v-model="planForm.symbol" type="text" required placeholder="AAPL" />
          </div>
          <div class="field">
            <label for="plan-amount">Amount per Interval (USD)</label>
            <input id="plan-amount" v-model.number="planForm.amountPerInterval" type="number" min="1" step="10" required />
          </div>
          <div class="field">
            <label for="plan-interval">Interval</label>
            <select id="plan-interval" v-model="planForm.interval" required>
              <option value="WEEKLY">Weekly</option>
              <option value="MONTHLY">Monthly</option>
            </select>
          </div>
          <div class="field">
            <label for="plan-start">Start Date</label>
            <input id="plan-start" v-model="planForm.startDate" type="date" required />
          </div>
          <div class="field">
            <label for="plan-end">End Date (Optional)</label>
            <input id="plan-end" v-model="planForm.endDate" type="date" />
          </div>
          <label class="field field--check">
            <input v-model="planForm.isActive" type="checkbox" />
            Active
          </label>
          <div class="form-actions">
            <button type="submit" class="btn btn--primary" :disabled="goalPlanStore.isLoadingPlans">
              {{ goalPlanStore.isLoadingPlans ? 'Saving...' : 'Save Plan' }}
            </button>
            <button type="button" class="btn btn--quiet" @click="resetPlanForm">Reset</button>
          </div>
        </form>

        <LoadingSpinner v-if="goalPlanStore.isLoadingPlans" message="Loading plans..." />
        <ul v-else-if="plans.length > 0" class="item-list">
          <li v-for="plan in plans" :key="plan.id" class="item">
            <div class="item-main">
              <div class="item-title">
                <h3>{{ plan.symbol }}</h3>
                <span class="status" :class="{ paused: !plan.isActive }">{{ plan.isActive ? 'Active' : 'Paused' }}</span>
              </div>
              <dl class="item-meta">
                <div>
                  <dt>Amount</dt>
                  <dd class="fig">${{ formatCurrency(plan.amountPerInterval) }}</dd>
                </div>
                <div>
                  <dt>Interval</dt>
                  <dd>{{ plan.interval }}</dd>
                </div>
                <div>
                  <dt>Duration</dt>
                  <dd class="fig">{{ formatPlanDuration(plan.startDate, plan.endDate) }}</dd>
                </div>
              </dl>
            </div>
            <button
              class="icon-btn"
              type="button"
              :aria-label="`Delete plan ${plan.symbol}`"
              title="Delete"
              @click="handleDeletePlan(plan.id)"
            >
              <Trash2 aria-hidden="true" />
            </button>
          </li>
        </ul>
        <EmptySheet v-else compact>등록된 투자 계획이 없습니다.</EmptySheet>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, X, Trash2 } from 'lucide-vue-next'
import TitleBlock from '@/components/pad/TitleBlock.vue'
import EmptySheet from '@/components/pad/EmptySheet.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
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

async function ensurePortfolio() {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return false
  }
  // 새로고침으로 직접 진입한 경우 포트폴리오를 먼저 불러온다 (대시보드로 튕기지 않도록)
  if (!portfolioStore.currentPortfolio) {
    await portfolioStore.fetchPortfolios()
  }
  if (!portfolioStore.currentPortfolio) {
    router.push('/dashboard')
    return false
  }
  return true
}

async function loadData() {
  if (!(await ensurePortfolio())) return
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

// 목표 달성률 (0-100%)
function progressOf(current: number, target: number) {
  if (!target || target <= 0) return 0
  return Math.min(100, Math.max(0, (current / target) * 100))
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
.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-12);
  align-items: start;
}

.panel .block-head {
  align-items: flex-end;
}

.panel .block-head p {
  margin-top: var(--space-1);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.entry {
  padding-bottom: var(--space-5);
  margin-bottom: var(--space-4);
  border-bottom: 3px double var(--rule-strong);
}

.span-2 {
  grid-column: 1 / -1;
}

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  gap: var(--space-2);
}

.item-list {
  list-style: none;
}

.item {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--rule);
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.item-title h3 {
  font-size: var(--text-xl);
  font-weight: 700;
}

.pct {
  font-size: var(--text-lg);
  font-weight: 600;
}

.progress {
  position: relative;
  height: 12px;
  margin-bottom: var(--space-3);
  border: 1.5px solid var(--rule-strong);
  /* unfilled part keeps the pad's grid */
  background-image: repeating-linear-gradient(90deg, transparent 0 9px, var(--rule) 9px 10px);
}

.progress-fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: repeating-linear-gradient(-45deg, var(--ink) 0 2px, transparent 2px 5px);
}

.status {
  font-family: var(--font-label);
  font-weight: 700;
  font-size: var(--text-xs);
  letter-spacing: var(--tracking-label);
  text-transform: uppercase;
  padding: 1px 6px;
  border: 1px solid currentColor;
}

.status.paused {
  color: var(--ink-3);
}

.item-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.item-meta dt {
  font-family: var(--font-label);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-label);
  text-transform: uppercase;
  color: var(--ink-3);
}

.item-meta dd {
  margin: 0;
  font-size: var(--text-sm);
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex: none;
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

@media (max-width: 1000px) {
  .content-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 560px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .item-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
