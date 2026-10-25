/**
 * Vue Router Configuration
 * Lazy loading으로 페이지별 코드 스플리팅 구현
 */

import { createRouter, createWebHistory, type RouteLocationNormalizedLoaded, type RouteRecordRaw } from 'vue-router'
import i18n from '@/i18n'
import { useAuthStore } from '@/stores/authStore'
import { whenSeedReady } from '@/utils/seedLoader'

// 라우트 정의 (lazy loading)
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'landing',
    component: () => import('@/pages/LandingPage.vue'),
    meta: {
      titleKey: 'titles.landing',
      requiresAuth: false
    }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: {
      titleKey: 'titles.login',
      requiresAuth: false
    }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/pages/DashboardPage.vue'),
    meta: {
      titleKey: 'titles.dashboard',
      requiresAuth: true
    }
  },
  {
    path: '/transactions',
    name: 'transactions',
    component: () => import('@/pages/TransactionsPage.vue'),
    meta: {
      titleKey: 'titles.transactions',
      requiresAuth: true
    }
  },
  {
    path: '/goals-plans',
    name: 'goals-plans',
    component: () => import('@/pages/GoalsPlansPage.vue'),
    meta: {
      titleKey: 'titles.goalsPlans',
      requiresAuth: true
    }
  },
  {
    path: '/backtest',
    name: 'backtest',
    component: () => import('@/pages/BacktestLabPage.vue'),
    meta: {
      titleKey: 'titles.backtest',
      requiresAuth: true
    }
  },
  {
    path: '/api-playground',
    name: 'api-playground',
    component: () => import('@/pages/ApiPlaygroundPage.vue'),
    meta: {
      titleKey: 'titles.apiPlayground',
      requiresAuth: false
    }
  },
  {
    path: '/docs',
    name: 'docs',
    component: () => import('@/pages/DocsPage.vue'),
    meta: {
      titleKey: 'titles.docs',
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
  scrollBehavior(to, from, savedPosition) {
    // 같은 화면에서 쿼리만 바뀌면(예: 거래 필터) 스크롤 위치를 유지
    if (to.path === from.path && to.hash === from.hash) {
      return false
    }
    if (savedPosition) {
      return savedPosition
    } else if (to.hash) {
      return { el: to.hash, behavior: 'smooth' }
    } else {
      return { top: 0 }
    }
  }
})

/** 라우트 메타의 titleKey를 현재 언어로 문서 제목에 반영 */
export function applyRouteTitle(route: Pick<RouteLocationNormalizedLoaded, 'meta'>) {
  if (typeof route.meta.titleKey === 'string') {
    document.title = i18n.global.t(route.meta.titleKey)
  }
}

// Navigation guards
router.beforeEach(async (to) => {
  // 페이지 타이틀 업데이트 (언어 전환 시에는 main.ts가 다시 적용)
  applyRouteTitle(to)

  // 인증 체크
  if (to.meta.requiresAuth) {
    const authStore = useAuthStore()
    if (!authStore.isAuthenticated) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    // 데모 데이터(캔들, 샘플 거래)가 준비된 뒤 화면을 연다
    await whenSeedReady()
  }

  return true
})

export default router
