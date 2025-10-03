/**
 * Goal & Plan Store
 * 재무 목표와 적립식 투자 계획 관리
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiClient } from '@/services/api'
import type {
  GoalResponse,
  CreateGoalRequest,
  PlanResponse,
  CreatePlanRequest
} from '@/services/api/schemas'
import { useAuthStore } from './authStore'

export const useGoalPlanStore = defineStore('goalPlan', () => {
  const authStore = useAuthStore()

  const goals = ref<GoalResponse[]>([])
  const plans = ref<PlanResponse[]>([])
  const isLoadingGoals = ref(false)
  const isLoadingPlans = ref(false)
  const error = ref<string | null>(null)

  async function fetchGoals(portfolioId: number) {
    if (!authStore.token) {
      error.value = '로그인이 필요합니다'
      return
    }

    isLoadingGoals.value = true
    error.value = null

    try {
      const response = await apiClient.getGoals(portfolioId, authStore.token)
      if (response.success && response.data) {
        goals.value = response.data
      } else {
        error.value = response.error?.message ?? '목표를 불러오지 못했습니다'
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '목표 조회 중 오류'
    } finally {
      isLoadingGoals.value = false
    }
  }

  async function createGoal(request: CreateGoalRequest) {
    if (!authStore.token) {
      error.value = '로그인이 필요합니다'
      return false
    }

    try {
      const response = await apiClient.createGoal(request, authStore.token)
      if (response.success && response.data) {
        goals.value.push(response.data)
        return true
      }
      error.value = response.error?.message ?? '목표 생성에 실패했습니다'
      return false
    } catch (err) {
      error.value = err instanceof Error ? err.message : '목표 생성 중 오류'
      return false
    }
  }

  async function deleteGoal(id: number) {
    if (!authStore.token) {
      error.value = '로그인이 필요합니다'
      return false
    }

    try {
      const response = await apiClient.deleteGoal(id, authStore.token)
      if (response.success) {
        goals.value = goals.value.filter((goal) => goal.id !== id)
        return true
      }
      error.value = response.error?.message ?? '목표 삭제에 실패했습니다'
      return false
    } catch (err) {
      error.value = err instanceof Error ? err.message : '목표 삭제 중 오류'
      return false
    }
  }

  async function fetchPlans(portfolioId: number) {
    if (!authStore.token) {
      error.value = '로그인이 필요합니다'
      return
    }

    isLoadingPlans.value = true
    error.value = null

    try {
      const response = await apiClient.getPlans(portfolioId, authStore.token)
      if (response.success && response.data) {
        plans.value = response.data
      } else {
        error.value = response.error?.message ?? '투자 계획을 불러오지 못했습니다'
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '투자 계획 조회 중 오류'
    } finally {
      isLoadingPlans.value = false
    }
  }

  async function createPlan(request: CreatePlanRequest) {
    if (!authStore.token) {
      error.value = '로그인이 필요합니다'
      return false
    }

    try {
      const response = await apiClient.createPlan(request, authStore.token)
      if (response.success && response.data) {
        plans.value.push(response.data)
        return true
      }
      error.value = response.error?.message ?? '투자 계획 생성에 실패했습니다'
      return false
    } catch (err) {
      error.value = err instanceof Error ? err.message : '투자 계획 생성 중 오류'
      return false
    }
  }

  async function deletePlan(id: number) {
    if (!authStore.token) {
      error.value = '로그인이 필요합니다'
      return false
    }

    try {
      const response = await apiClient.deletePlan(id, authStore.token)
      if (response.success) {
        plans.value = plans.value.filter((plan) => plan.id !== id)
        return true
      }
      error.value = response.error?.message ?? '투자 계획 삭제에 실패했습니다'
      return false
    } catch (err) {
      error.value = err instanceof Error ? err.message : '투자 계획 삭제 중 오류'
      return false
    }
  }

  function clearError() {
    error.value = null
  }

  function reset() {
    goals.value = []
    plans.value = []
    error.value = null
  }

  return {
    goals,
    plans,
    isLoadingGoals,
    isLoadingPlans,
    error,
    fetchGoals,
    createGoal,
    deleteGoal,
    fetchPlans,
    createPlan,
    deletePlan,
    clearError,
    reset
  }
})
