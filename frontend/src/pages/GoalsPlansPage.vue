<template>
  <div class="page goals-plans-page">
    <TitleBlock :title="t('goals.title')" sheet="03 / 04">
      {{ t('goals.subtitle') }}
    </TitleBlock>

    <EmptySheet v-if="!authStore.isAuthenticated">
      {{ t('goals.pleaseLogin') }}
      <template #action>
        <router-link to="/login" class="btn btn--primary">{{ t('common.login') }}</router-link>
      </template>
    </EmptySheet>

    <EmptySheet v-else-if="!portfolioStore.currentPortfolio">
      {{ t('goals.noPortfolio') }}
      <template #action>
        <router-link to="/dashboard" class="btn btn--primary">{{ t('common.goToDashboard') }}</router-link>
      </template>
    </EmptySheet>

    <div v-else class="content-grid">
      <section class="panel" aria-labelledby="goals-heading">
        <div class="block-head">
          <div>
            <h2 id="goals-heading">{{ t('goals.goalsHeading') }}</h2>
            <p>{{ t('goals.goalsBody') }}</p>
          </div>
          <button
            type="button"
            class="btn btn--sm"
            :aria-expanded="showGoalForm"
            aria-controls="goal-form"
            @click="toggleGoalForm"
          >
            <component :is="showGoalForm ? X : Plus" aria-hidden="true" />
            {{ showGoalForm ? t('common.cancel') : t('goals.addGoal') }}
          </button>
        </div>

        <form v-if="showGoalForm" id="goal-form" class="form-grid entry" @submit.prevent="handleCreateGoal">
          <div class="field span-2">
            <label for="goal-name">{{ t('goals.goalName') }}</label>
            <input id="goal-name" v-model="goalForm.name" type="text" required :placeholder="t('goals.goalNamePlaceholder')" />
          </div>
          <div class="field">
            <label for="goal-target">{{ t('goals.targetAmountUsd') }}</label>
            <input id="goal-target" v-model.number="goalForm.targetAmount" type="number" min="1" step="100" required />
          </div>
          <div class="field">
            <label for="goal-current">{{ t('goals.currentAmount') }}</label>
            <input id="goal-current" v-model.number="goalForm.currentAmount" type="number" min="0" step="100" />
          </div>
          <div class="field">
            <label for="goal-date">{{ t('goals.targetDate') }}</label>
            <input id="goal-date" v-model="goalForm.targetDate" type="date" required />
          </div>
          <div class="form-actions">
            <button type="submit" class="btn btn--primary" :disabled="goalPlanStore.isLoadingGoals">
              {{ goalPlanStore.isLoadingGoals ? t('common.saving') : t('goals.saveGoal') }}
            </button>
            <button type="button" class="btn btn--quiet" @click="resetGoalForm">{{ t('common.reset') }}</button>
          </div>
        </form>

        <LoadingSpinner v-if="goalPlanStore.isLoadingGoals" :message="t('goals.loadingGoals')" />
        <ul v-else-if="goals.length > 0" class="item-list">
          <li v-for="goal in goals" :key="goal.id" class="item">
            <div class="item-main">
              <div class="item-title">
                <h3>{{ goal.name }}</h3>
                <span class="pct fig">{{ formatNumber(progressOf(goal.currentAmount, goal.targetAmount), locale, 0) }}%</span>
              </div>
              <div
                class="progress"
                role="progressbar"
                :aria-valuenow="Math.round(progressOf(goal.currentAmount, goal.targetAmount))"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-label="t('goals.progressLabel', { name: goal.name })"
              >
                <span class="progress-fill" :style="{ width: `${progressOf(goal.currentAmount, goal.targetAmount)}%` }"></span>
              </div>
              <dl class="item-meta">
                <div>
                  <dt>{{ t('goals.currentAmount') }}</dt>
                  <dd class="fig">{{ formatUsd(goal.currentAmount, locale, 0) }}</dd>
                </div>
                <div>
                  <dt>{{ t('goals.targetAmount') }}</dt>
                  <dd class="fig">{{ formatUsd(goal.targetAmount, locale, 0) }}</dd>
                </div>
                <div>
                  <dt>{{ t('goals.targetDate') }}</dt>
                  <dd class="fig">{{ formatDate(goal.targetDate, locale) }}</dd>
                </div>
              </dl>
            </div>
            <button
              class="icon-btn"
              type="button"
              :aria-label="t('goals.deleteGoal', { name: goal.name })"
              :title="t('common.delete')"
              @click="handleDeleteGoal(goal.id)"
            >
              <Trash2 aria-hidden="true" />
            </button>
          </li>
        </ul>
        <EmptySheet v-else compact>{{ t('goals.noGoals') }}</EmptySheet>
      </section>

      <section class="panel" aria-labelledby="plans-heading">
        <div class="block-head">
          <div>
            <h2 id="plans-heading">{{ t('goals.plansHeading') }}</h2>
            <p>{{ t('goals.plansBody') }}</p>
          </div>
          <button
            type="button"
            class="btn btn--sm"
            :aria-expanded="showPlanForm"
            aria-controls="plan-form"
            @click="togglePlanForm"
          >
            <component :is="showPlanForm ? X : Plus" aria-hidden="true" />
            {{ showPlanForm ? t('common.cancel') : t('goals.addPlan') }}
          </button>
        </div>

        <form v-if="showPlanForm" id="plan-form" class="form-grid entry" @submit.prevent="handleCreatePlan">
          <div class="field">
            <label for="plan-symbol">{{ t('goals.symbol') }}</label>
            <input id="plan-symbol" v-model="planForm.symbol" type="text" required placeholder="AAPL" />
          </div>
          <div class="field">
            <label for="plan-amount">{{ t('goals.amountPerIntervalUsd') }}</label>
            <input id="plan-amount" v-model.number="planForm.amountPerInterval" type="number" min="1" step="10" required />
          </div>
          <div class="field">
            <label for="plan-interval">{{ t('goals.interval') }}</label>
            <select id="plan-interval" v-model="planForm.interval" required>
              <option value="WEEKLY">{{ t('goals.weekly') }}</option>
              <option value="MONTHLY">{{ t('goals.monthly') }}</option>
            </select>
          </div>
          <div class="field">
            <label for="plan-start">{{ t('goals.startDate') }}</label>
            <input id="plan-start" v-model="planForm.startDate" type="date" required />
          </div>
          <div class="field">
            <label for="plan-end">{{ t('goals.endDateOptional') }}</label>
            <input id="plan-end" v-model="planForm.endDate" type="date" />
          </div>
          <label class="field field--check">
            <input v-model="planForm.isActive" type="checkbox" />
            {{ t('goals.activeCheckbox') }}
          </label>
          <div class="form-actions">
            <button type="submit" class="btn btn--primary" :disabled="goalPlanStore.isLoadingPlans">
              {{ goalPlanStore.isLoadingPlans ? t('common.saving') : t('goals.savePlan') }}
            </button>
            <button type="button" class="btn btn--quiet" @click="resetPlanForm">{{ t('common.reset') }}</button>
          </div>
        </form>

        <LoadingSpinner v-if="goalPlanStore.isLoadingPlans" :message="t('goals.loadingPlans')" />
        <ul v-else-if="plans.length > 0" class="item-list">
          <li v-for="plan in plans" :key="plan.id" class="item">
            <div class="item-main">
              <div class="item-title">
                <h3>{{ plan.symbol }}</h3>
                <span class="status" :class="{ paused: !plan.isActive }">{{ plan.isActive ? t('goals.active') : t('goals.paused') }}</span>
              </div>
              <dl class="item-meta">
                <div>
                  <dt>{{ t('goals.amount') }}</dt>
                  <dd class="fig">{{ formatUsd(plan.amountPerInterval, locale, 0) }}</dd>
                </div>
                <div>
                  <dt>{{ t('goals.interval') }}</dt>
                  <dd>{{ intervalLabel(plan.interval) }}</dd>
                </div>
                <div>
                  <dt>{{ t('goals.duration') }}</dt>
                  <dd class="fig">{{ formatPlanDuration(plan.startDate, plan.endDate) }}</dd>
                </div>
              </dl>
            </div>
            <button
              class="icon-btn"
              type="button"
              :aria-label="t('goals.deletePlan', { symbol: plan.symbol })"
              :title="t('common.delete')"
              @click="handleDeletePlan(plan.id)"
            >
              <Trash2 aria-hidden="true" />
            </button>
          </li>
        </ul>
        <EmptySheet v-else compact>{{ t('goals.noPlans') }}</EmptySheet>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Plus, X, Trash2 } from 'lucide-vue-next'
import TitleBlock from '@/components/pad/TitleBlock.vue'
import EmptySheet from '@/components/pad/EmptySheet.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useAuthStore } from '@/stores/authStore'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { useGoalPlanStore } from '@/stores/goalPlanStore'
import { useToast } from '@/composables/useToast'
import { toDateInputValue } from '@/utils/dateInput'
import { formatDate, formatNumber, formatUsd } from '@/utils/localeFormat'
import { failureMessage } from '@/utils/localizedMessages'

const router = useRouter()
const { t, locale } = useI18n()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const goalPlanStore = useGoalPlanStore()
const toast = useToast()

const showGoalForm = ref(false)
const showPlanForm = ref(false)

const goals = computed(() => goalPlanStore.goals)
const plans = computed(() => goalPlanStore.plans)

function intervalLabel(interval: string): string {
  if (interval === 'WEEKLY') return t('goals.weekly')
  if (interval === 'MONTHLY') return t('goals.monthly')
  return interval
}

const goalForm = reactive({
  name: '',
  targetAmount: 10000,
  currentAmount: 0,
  targetDate: toDateInputValue()
})

const planForm = reactive({
  symbol: 'AAPL',
  amountPerInterval: 500,
  interval: 'MONTHLY' as 'WEEKLY' | 'MONTHLY',
  startDate: toDateInputValue(),
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
  if (goalPlanStore.error) {
    toast.error(failureMessage('goals.loadFailed', goalPlanStore.error))
  }
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
  goalForm.targetDate = toDateInputValue()
  goalPlanStore.clearError()
}

function resetPlanForm() {
  planForm.symbol = 'AAPL'
  planForm.amountPerInterval = 500
  planForm.interval = 'MONTHLY'
  planForm.startDate = toDateInputValue()
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
    toast.success(t('goals.goalSaved'))
    resetGoalForm()
    showGoalForm.value = false
  } else {
    toast.error(failureMessage('goals.goalSaveFailed', goalPlanStore.error))
  }
}

async function handleDeleteGoal(goalId: number) {
  if (!confirm(t('goals.confirmDeleteGoal'))) return
  const success = await goalPlanStore.deleteGoal(goalId)
  if (success) {
    toast.warning(t('goals.goalDeleted'))
  } else {
    toast.error(failureMessage('goals.goalDeleteFailed', goalPlanStore.error))
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
    toast.success(t('goals.planSaved'))
    resetPlanForm()
    showPlanForm.value = false
  } else {
    toast.error(failureMessage('goals.planSaveFailed', goalPlanStore.error))
  }
}

async function handleDeletePlan(planId: number) {
  if (!confirm(t('goals.confirmDeletePlan'))) return
  const success = await goalPlanStore.deletePlan(planId)
  if (success) {
    toast.warning(t('goals.planDeleted'))
  } else {
    toast.error(failureMessage('goals.planDeleteFailed', goalPlanStore.error))
  }
}

// 목표 달성률 (0-100%)
function progressOf(current: number, target: number) {
  if (!target || target <= 0) return 0
  return Math.min(100, Math.max(0, (current / target) * 100))
}

function formatPlanDuration(start: string, end?: string) {
  const from = formatDate(start, locale.value)
  return `${from} → ${end ? formatDate(end, locale.value) : t('goals.ongoing')}`
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
  color: var(--ink-2);
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
