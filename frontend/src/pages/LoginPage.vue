<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1>FinLab</h1>
        <p class="subtitle">Portfolio Management Demo</p>
      </div>

      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="email">Email</label>
          <input
            id="email"
            v-model="email"
            type="email"
            placeholder="demo@finlab.app"
            required
            :disabled="authStore.isLoading"
          />
        </div>

        <button type="submit" class="btn-login" :disabled="authStore.isLoading">
          {{ authStore.isLoading ? 'Logging in...' : 'Login' }}
        </button>

        <p v-if="authStore.error" class="error-message">
          {{ authStore.error }}
        </p>

        <p class="hint">
          Hint: Try <code>demo@finlab.app</code> or any valid email
        </p>
      </form>

      <div class="demo-info">
        <p>
          <strong>Mock Authentication</strong> - No password required. Any email creates/logs
          into a user.
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const router = useRouter()
const authStore = useAuthStore()

// 이메일 입력
const email = ref('demo@finlab.app')

// 로그인 처리
async function handleLogin() {
  authStore.clearError()

  const success = await authStore.login(email.value)

  if (success) {
    // 로그인 성공 시 대시보드로 이동
    router.push('/dashboard')
  }
}

// 이미 로그인되어 있으면 대시보드로 리디렉트
onMounted(() => {
  if (authStore.isAuthenticated) {
    router.push('/dashboard')
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
  padding: var(--space-8);
  background: linear-gradient(135deg, var(--color-primary-50) 0%, var(--color-secondary-50) 100%);
}

.login-card {
  background: var(--color-background);
  border-radius: var(--border-radius-xl);
  box-shadow: var(--shadow-lg);
  padding: var(--space-8);
  max-width: 400px;
  width: 100%;
}

.login-header {
  text-align: center;
  margin-bottom: var(--space-8);
}

.login-header h1 {
  font-size: var(--font-size-3xl);
  color: var(--color-primary-600);
  margin-bottom: var(--space-2);
}

.subtitle {
  color: var(--color-text-muted);
  margin: 0;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form-group label {
  font-weight: var(--font-weight-medium);
  color: var(--color-text);
  font-size: var(--font-size-sm);
}

.form-group input {
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-base);
  transition: all var(--transition-base);
}

.form-group input:focus {
  outline: none;
  border-color: var(--color-primary-500);
  box-shadow: 0 0 0 3px var(--color-primary-100);
}

.form-group input:disabled {
  background-color: var(--color-gray-100);
  cursor: not-allowed;
}

.btn-login {
  padding: var(--space-3) var(--space-6);
  background-color: var(--color-primary-600);
  color: white;
  border: none;
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn-login:hover:not(:disabled) {
  background-color: var(--color-primary-700);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.btn-login:disabled {
  background-color: var(--color-gray-400);
  cursor: not-allowed;
  transform: none;
}

.error-message {
  color: var(--color-error-600);
  font-size: var(--font-size-sm);
  text-align: center;
  margin: 0;
  padding: var(--space-2);
  background-color: var(--color-error-50);
  border-radius: var(--border-radius-md);
}

.hint {
  text-align: center;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  margin: 0;
}

.hint code {
  background-color: var(--color-gray-100);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--border-radius-sm);
  font-family: var(--font-mono);
}

.demo-info {
  margin-top: var(--space-6);
  padding-top: var(--space-6);
  border-top: 1px solid var(--color-border);
  text-align: center;
}

.demo-info p {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin: 0;
}
</style>
