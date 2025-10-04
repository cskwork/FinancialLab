/**
 * Vue Router Configuration
 * Lazy loading으로 페이지별 코드 스플리팅 구현
 */

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

// 라우트 정의 (lazy loading)
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'landing',
    component: () => import('@/pages/LandingPage.vue'),
    meta: {
      title: 'FinancialLab - 금융 분석 플랫폼',
      requiresAuth: false
    }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: {
      title: '로그인 - FinancialLab',
      requiresAuth: false
    }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/pages/DashboardPage.vue'),
    meta: {
      title: '대시보드 - FinancialLab',
      requiresAuth: true
    }
  },
  {
    path: '/transactions',
    name: 'transactions',
    component: () => import('@/pages/TransactionsPage.vue'),
    meta: {
      title: '거래 내역 - FinancialLab',
      requiresAuth: true
    }
  },
  {
    path: '/goals-plans',
    name: 'goals-plans',
    component: () => import('@/pages/GoalsPlansPage.vue'),
    meta: {
      title: '목표 및 계획 - FinancialLab',
      requiresAuth: true
    }
  },
  {
    path: '/backtest',
    name: 'backtest',
    component: () => import('@/pages/BacktestLabPage.vue'),
    meta: {
      title: '백테스트 연구소 - FinancialLab',
      requiresAuth: true
    }
  },
  {
    path: '/api-playground',
    name: 'api-playground',
    component: () => import('@/pages/ApiPlaygroundPage.vue'),
    meta: {
      title: 'API 플레이그라운드 - FinancialLab',
      requiresAuth: false
    }
  },
  {
    path: '/docs',
    name: 'docs',
    component: () => import('@/pages/DocsPage.vue'),
    meta: {
      title: '문서화 - FinancialLab',
      requiresAuth: false
    }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    redirect: '/'
  }
]

// Router 인스턴스 생성
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else if (to.hash) {
      return { el: to.hash, behavior: 'smooth' }
    } else {
      return { top: 0 }
    }
  }
})

// Navigation guards
router.beforeEach((to, _from, next) => {
  // 페이지 타이틀 업데이트
  if (to.meta.title) {
    document.title = to.meta.title as string
  }

  // 인증 체크 (나중에 auth store와 연동)
  // const requiresAuth = to.meta.requiresAuth
  // if (requiresAuth && !isAuthenticated()) {
  //   next({ name: 'login', query: { redirect: to.fullPath } })
  // } else {
  //   next()
  // }

  next()
})

export default router
