<template>
  <nav class="navbar">
    <div class="navbar-container">
      <router-link to="/" class="navbar-brand">
        <span class="brand-logo">📊</span>
        <span class="brand-name">FinLab</span>
      </router-link>

      <div v-if="authStore.isAuthenticated" class="navbar-menu">
        <router-link to="/dashboard" class="nav-link" active-class="active">
          Dashboard
        </router-link>
        <router-link to="/transactions" class="nav-link" active-class="active">
          Transactions
        </router-link>
        <router-link to="/goals-plans" class="nav-link" active-class="active">
          Goals & Plans
        </router-link>
        <router-link to="/backtest" class="nav-link" active-class="active">
          Backtest Lab
        </router-link>
      </div>

      <div class="navbar-right">
        <div class="backend-control" role="group" aria-label="Backend mode toggle">
          <label class="control-label" for="backend-mode">Backend</label>
          <select
            id="backend-mode"
            v-model="backendMode"
            class="backend-select"
            aria-describedby="backend-mode-hint"
          >
            <option value="mock">Mock</option>
            <option value="live">Live</option>
          </select>
          <input
            id="backend-url"
            v-model="backendUrl"
            type="url"
            class="backend-input"
            :disabled="backendMode === 'mock'"
            placeholder="https://finlab-api.fly.dev"
          />
          <button
            type="button"
            class="btn-connect"
            :disabled="backendMode === 'mock'"
            @click="handleConnectLive"
          >
            Connect
          </button>
          <span id="backend-mode-hint" class="control-hint">Mock 모드는 오프라인에서도 동작합니다.</span>
        </div>
        <span v-if="authStore.isAuthenticated" class="user-email">
          {{ authStore.currentUserEmail }}
        </span>
        <button v-if="authStore.isAuthenticated" @click="handleLogout" class="btn-logout">
          Logout
        </button>
        <router-link v-else to="/login" class="btn-login"> Login </router-link>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { useGoalPlanStore } from '@/stores/goalPlanStore'
import { useAnalyticsStore } from '@/stores/analyticsStore'
import { useConfigStore, type BackendMode } from '@/stores/configStore'
import { useToast } from '@/composables/useToast'

const router = useRouter()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const transactionStore = useTransactionStore()
const goalPlanStore = useGoalPlanStore()
const analyticsStore = useAnalyticsStore()
const configStore = useConfigStore()
const toast = useToast()

const backendMode = computed({
  get: () => configStore.backendMode,
  set: (value: BackendMode) => {
    configStore.setBackendMode(value)
    toast.info(value === 'mock' ? 'Mock API 모드로 전환했습니다.' : 'Live API 모드로 전환했습니다.')
  }
})

const backendUrl = computed({
  get: () => configStore.backendUrl,
  set: (value) => configStore.setBackendUrl(value.trim())
})

// 로그아웃 처리
function handleLogout() {
  authStore.logout()
  portfolioStore.reset()
  transactionStore.reset()
  goalPlanStore.reset()
  analyticsStore.reset()
  router.push('/login')
}

function handleConnectLive() {
  if (!backendUrl.value) {
    toast.warning('라이브 백엔드 URL을 입력하세요.')
    return
  }

  configStore.setBackendMode('live')
  toast.success('Live API 모드로 연결을 시도합니다.')
}
</script>

<style scoped>
.navbar {
  background-color: var(--color-background);
  border-bottom: 1px solid var(--color-border);
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: var(--shadow-sm);
}

.navbar-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--space-4) var(--space-6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  text-decoration: none;
  font-weight: var(--font-weight-bold);
  font-size: var(--font-size-lg);
  color: var(--color-text);
}

.brand-logo {
  font-size: var(--font-size-2xl);
}

.brand-name {
  color: var(--color-primary-600);
}

.navbar-menu {
  display: flex;
  gap: var(--space-4);
  flex: 1;
}

.nav-link {
  padding: var(--space-2) var(--space-4);
  text-decoration: none;
  color: var(--color-text-muted);
  font-weight: var(--font-weight-medium);
  border-radius: var(--border-radius-md);
  transition: all var(--transition-base);
}

.nav-link:hover {
  color: var(--color-text);
  background-color: var(--color-gray-100);
}

.nav-link.active {
  color: var(--color-primary-600);
  background-color: var(--color-primary-50);
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.backend-control {
  display: grid;
  grid-template-columns: auto auto minmax(220px, 1fr) auto;
  gap: var(--space-2);
  align-items: center;
}

.control-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.backend-select,
.backend-input {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
}

.backend-input:disabled {
  background-color: var(--color-gray-100);
  color: var(--color-text-muted);
}

.btn-connect {
  padding: var(--space-2) var(--space-4);
  background-color: var(--color-primary-600);
  color: #fff;
  border: none;
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn-connect:disabled {
  background-color: var(--color-gray-300);
  cursor: not-allowed;
}

.btn-connect:not(:disabled):hover {
  background-color: var(--color-primary-700);
}

.control-hint {
  grid-column: 1 / -1;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.user-email {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.btn-logout,
.btn-login {
  padding: var(--space-2) var(--space-4);
  background-color: var(--color-gray-200);
  color: var(--color-text);
  border: none;
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
  text-decoration: none;
  display: inline-block;
}

.btn-logout:hover,
.btn-login:hover {
  background-color: var(--color-gray-300);
}

@media (max-width: 768px) {
  .navbar-container {
    flex-direction: column;
    gap: var(--space-3);
  }

  .navbar-menu {
    flex-direction: column;
    width: 100%;
  }

  .navbar-right {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .backend-control {
    grid-template-columns: repeat(2, 1fr);
    width: 100%;
  }

  .backend-input {
    min-width: 0;
  }

  .user-email {
    display: none;
  }
}
</style>
