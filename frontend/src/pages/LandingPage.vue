<template>
  <div class="landing-page">
    <header class="hero" role="banner">
      <div class="hero-content">
        <h1>FinLab</h1>
        <p class="tagline">Full-stack fintech simulation: Vue 3 frontend + Java Spring Boot backend</p>
        <div class="cta-buttons" role="group" aria-label="Primary actions">
          <router-link to="/dashboard" class="btn btn-primary">
            🚀 Try Dashboard
          </router-link>
          <router-link to="/api-playground" class="btn btn-secondary">
            📐 Try API Playground
          </router-link>
          <router-link to="/docs" class="btn btn-secondary">
            ☕ Java Docs & Coverage
          </router-link>
        </div>
      </div>
      <div class="hero-media" aria-label="Product walkthrough placeholder">
        <div class="demo-placeholder">
          <p>🎥 Demo video coming soon...</p>
        </div>
      </div>
    </header>

    <section class="mode-switcher" aria-label="Backend mode toggle description">
      <h2>Backend Mode</h2>
      <div class="mode-card">
        <p>Switch between a deterministic mock API and a live Spring Boot backend deployment.</p>
        <div class="mode-actions">
          <button
            type="button"
            class="btn-switch"
            :aria-pressed="isMockMode"
            @click="activateMockMode"
          >
            Mock
          </button>
          <button
            type="button"
            class="btn-switch"
            :aria-pressed="!isMockMode"
            :disabled="!backendUrl"
            @click="activateLiveMode"
          >
            Live (Connect)
          </button>
        </div>
        <label class="mode-url" for="landing-backend-url">
          Live URL
          <input
            id="landing-backend-url"
            v-model="backendUrl"
            type="url"
            placeholder="https://finlab-api.fly.dev"
          />
        </label>
        <span class="mode-hint">Mock 모드는 오프라인에서도 완전하게 동작합니다.</span>
      </div>
    </section>

    <section class="features" aria-labelledby="feature-heading">
      <h2 id="feature-heading">Feature Highlights</h2>
      <div class="feature-grid">
        <article class="feature-card">
          <h3>📊 Portfolio Tracking</h3>
          <p>FIFO cost basis with deterministic holdings reconciliation and IndexedDB persistence.</p>
        </article>
        <article class="feature-card">
          <h3>🧪 DCA Backtesting</h3>
          <p>10k+ OHLCV candles powered by dedicated Web Workers for smooth simulations.</p>
        </article>
        <article class="feature-card">
          <h3>📈 Risk Metrics</h3>
          <p>Sharpe ratio, max drawdown, and volatility calculated off-thread for reliability.</p>
        </article>
        <article class="feature-card">
          <h3>🔌 OpenAPI Contract</h3>
          <p>Swagger-backed playground with mock/live toggle and RBAC-aware handlers.</p>
        </article>
      </div>
    </section>

    <footer class="disclaimer">
      <p>
        Educational simulation. No real money. Java backend optional.
        <router-link to="/docs" class="footer-link">(deploy guide)</router-link>
      </p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useConfigStore } from '@/stores/configStore'
import { useToast } from '@/composables/useToast'

// Landing page - 프로젝트 소개 및 CTA
const configStore = useConfigStore()
const toast = useToast()

const isMockMode = computed(() => configStore.isMockMode)
const backendUrl = computed({
  get: () => configStore.backendUrl,
  set: (value) => configStore.setBackendUrl(value.trim())
})

function activateMockMode() {
  configStore.setBackendMode('mock')
  toast.info('Mock API 모드로 전환했습니다.')
}

function activateLiveMode() {
  if (!backendUrl.value) {
    toast.warning('라이브 백엔드 URL을 입력한 뒤 다시 시도하세요.')
    return
  }
  configStore.setBackendMode('live')
  toast.success('Live API 모드 연결을 시도합니다.')
}
</script>

<style scoped>
.landing-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-8);
  background: linear-gradient(180deg, rgba(37, 99, 235, 0.05) 0%, rgba(255, 255, 255, 0.9) 100%);
}

.hero {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: var(--space-10);
  width: 100%;
  max-width: 1200px;
  margin-bottom: var(--space-16);
  align-items: center;
}

.hero-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.hero h1 {
  font-size: var(--font-size-4xl);
  margin: 0;
  color: var(--color-primary-600);
}

.tagline {
  font-size: var(--font-size-lg);
  color: var(--color-text-muted);
  margin: 0;
  max-width: 600px;
}

.cta-buttons {
  display: flex;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.hero-media video {
  width: 100%;
  border-radius: var(--border-radius-2xl);
  box-shadow: var(--shadow-xl);
  border: 1px solid rgba(37, 99, 235, 0.15);
}

.btn {
  display: inline-block;
  padding: var(--space-3) var(--space-6);
  border-radius: var(--border-radius-lg);
  font-weight: var(--font-weight-medium);
  transition: all var(--transition-base);
  text-decoration: none;
}

.btn-primary {
  background-color: var(--color-primary-600);
  color: white;
}

.btn-primary:hover {
  background-color: var(--color-primary-700);
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
  text-decoration: none;
}

.btn-secondary {
  background-color: var(--color-gray-200);
  color: var(--color-text);
}

.btn-secondary:hover {
  background-color: var(--color-gray-300);
  text-decoration: none;
}

.mode-switcher {
  width: 100%;
  max-width: 960px;
  margin-bottom: var(--space-16);
}

.mode-card {
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-xl);
  padding: var(--space-6);
  background-color: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.mode-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
}

.mode-url {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.mode-url input {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-size: var(--font-size-sm);
}

.btn-switch {
  padding: var(--space-2) var(--space-5);
  border-radius: var(--border-radius-full);
  border: 1px solid var(--color-border);
  background-color: var(--color-gray-100);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn-switch[aria-pressed='true'] {
  background-color: var(--color-primary-600);
  color: #fff;
  border-color: transparent;
}

.btn-switch:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.mode-hint {
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.features {
  max-width: 1200px;
  margin-bottom: var(--space-16);
  width: 100%;
}

.features h2 {
  text-align: center;
  margin-bottom: var(--space-8);
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: var(--space-6);
}

.feature-card {
  padding: var(--space-6);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-xl);
  background-color: var(--color-background);
  transition: all var(--transition-base);
  min-height: 180px;
}

.feature-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-4px);
}

.feature-card h3 {
  margin-bottom: var(--space-3);
  font-size: var(--font-size-lg);
}

.feature-card p {
  color: var(--color-text-muted);
  margin: 0;
}

.disclaimer {
  text-align: center;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.footer-link {
  color: var(--color-primary-600);
  margin-left: var(--space-2);
}

@media (max-width: 768px) {
  .landing-page {
    padding: var(--space-6);
  }

  .hero {
    grid-template-columns: 1fr;
    text-align: center;
  }

  .cta-buttons {
    justify-content: center;
  }

  .hero-media video {
    max-width: 420px;
    margin: 0 auto;
  }
}
</style>
