<template>
  <div class="login-page">
    <div class="login-top">
      <router-link to="/" class="back-link" :aria-label="t('login.backHome')">
        <ArrowLeft aria-hidden="true" />
        FinLab
      </router-link>
      <LocaleSelect id="login-language" compact />
    </div>

    <div class="login-sheet">
      <header class="login-header">
        <svg class="login-mark" viewBox="0 0 32 32" aria-hidden="true">
          <rect x="1" y="1" width="30" height="30" fill="none" stroke="currentColor" stroke-width="2" />
          <path d="M5 24 L11 17 L16 20 L27 7" fill="none" stroke="currentColor" stroke-width="2.6" />
          <path d="M5 27 H27" stroke="var(--highlight)" stroke-width="3" />
        </svg>
        <h1>{{ t('login.title') }}</h1>
        <p class="subtitle">{{ t('login.subtitle') }}</p>
      </header>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="field">
          <label for="email">{{ t('login.email') }}</label>
          <input
            id="email"
            v-model="email"
            type="email"
            autocomplete="email"
            :placeholder="t('login.emailPlaceholder')"
            required
            :disabled="authStore.isLoading"
            :aria-invalid="Boolean(authStore.error)"
            :aria-describedby="authStore.error ? 'login-error' : 'login-hint'"
          />
        </div>

        <div class="field">
          <label for="password">{{ t('login.password') }}</label>
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            maxlength="72"
            :placeholder="t('login.passwordPlaceholder')"
            required
            :disabled="authStore.isLoading"
            :aria-invalid="Boolean(authStore.error)"
            :aria-describedby="authStore.error ? 'login-error' : 'login-hint'"
          />
        </div>

        <button type="submit" class="btn btn--primary login-submit" :disabled="authStore.isLoading">
          <LogIn aria-hidden="true" />
          {{ authStore.isLoading ? t('login.loggingIn') : t('login.loginButton') }}
        </button>

        <p v-if="authStore.error" id="login-error" class="error-message" role="alert">
          {{ localizeError(authStore.error) }}
        </p>

        <p id="login-hint" class="hint">
          {{ configStore.isMockMode ? t('login.hint') : t('login.liveHint') }}
        </p>
      </form>

      <p class="demo-info">
        {{ configStore.isMockMode ? t('login.mockAuthInfo') : t('login.liveAuthInfo') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, LogIn } from 'lucide-vue-next'
import { useAuthStore, DEMO_PASSWORD } from '@/stores/authStore'
import { useConfigStore } from '@/stores/configStore'
import { localizeError } from '@/utils/localizedMessages'
import LocaleSelect from '@/components/common/LocaleSelect.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const authStore = useAuthStore()
const configStore = useConfigStore()

// 이메일 입력
const email = ref('demo@finlab.app')
const password = ref(configStore.isMockMode ? DEMO_PASSWORD : '')

// 로그인 전 요청했던 앱 내부 경로로 복귀 (외부 URL은 무시)
function redirectTarget(): string {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/dashboard'
}

// 로그인 처리
async function handleLogin() {
  authStore.clearError()
  const isMockLogin = configStore.isMockMode
  let success = false

  try {
    success = await authStore.login(email.value, password.value)
    if (success) {
      router.push(redirectTarget())
    }
  } finally {
    password.value = isMockLogin && configStore.isMockMode && !success ? DEMO_PASSWORD : ''
  }
}

// 이미 로그인되어 있으면 대시보드로 리디렉트
onMounted(() => {
  if (authStore.isAuthenticated) {
    router.push(redirectTarget())
  }
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-6);
  padding: var(--space-8) var(--space-4);
}

.login-top {
  width: min(420px, 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 44px;
  color: var(--ink);
  font-weight: 600;
  text-decoration: none;
}

.back-link svg {
  width: 18px;
  height: 18px;
}

.login-sheet {
  width: min(420px, 100%);
  padding: var(--space-8);
  background-color: var(--paper);
  border: 1.5px solid var(--rule-strong);
}

.login-header {
  padding-bottom: var(--space-5);
  margin-bottom: var(--space-6);
  border-bottom: 2px solid var(--rule-strong);
}

.login-mark {
  width: 36px;
  height: 36px;
  margin-bottom: var(--space-4);
}

.login-header h1 {
  font-size: var(--text-display);
  line-height: 0.9;
  font-weight: 700;
}

.subtitle {
  margin: var(--space-2) 0 0;
  color: var(--ink-2);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.login-submit {
  width: 100%;
  min-height: 52px;
  font-size: var(--text-lg);
}

.error-message {
  margin: 0;
  padding: var(--space-3);
  color: var(--red-pencil);
  border: 1.5px solid var(--red-pencil);
  font-size: var(--text-sm);
}

.hint {
  margin: 0;
  color: var(--ink-2);
  font-size: var(--text-sm);
}

.demo-info {
  margin: var(--space-6) 0 0;
  padding-top: var(--space-4);
  border-top: 1px solid var(--rule);
  font-size: var(--text-sm);
  color: var(--ink-2);
}
</style>
