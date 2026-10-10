<template>
  <div class="landing">
    <div class="landing-frame">
      <header class="cover-block" role="banner">
        <div class="cb-cell cb-brand">
          <svg class="cb-mark" viewBox="0 0 32 32" aria-hidden="true">
            <rect x="1" y="1" width="30" height="30" fill="none" stroke="currentColor" stroke-width="2" />
            <path d="M5 24 L11 17 L16 20 L27 7" fill="none" stroke="currentColor" stroke-width="2.6" />
            <path d="M5 27 H27" stroke="var(--highlight)" stroke-width="3" />
          </svg>
          <span>FinLab</span>
        </div>
        <div class="cb-cell cb-subject">
          <span class="cb-key">Subject</span>
          <span>Portfolio simulation</span>
        </div>
        <div class="cb-cell cb-sheet">
          <span class="cb-key">Sheet</span>
          <span class="fig">00 · Cover</span>
        </div>
        <nav class="cb-cell cb-links" aria-label="Cover links">
          <router-link to="/api-playground">API Playground</router-link>
          <router-link to="/docs">Docs</router-link>
          <router-link v-if="!isAuthenticated" to="/login">Login</router-link>
        </nav>
      </header>

      <section class="hero" aria-labelledby="hero-title">
        <div class="hero-copy">
          <h1 id="hero-title" class="hero-title">FinLab</h1>
          <p class="tagline">Full-stack fintech simulation: Vue 3 frontend + Java Spring Boot backend</p>
          <div class="cta-buttons" role="group" aria-label="Primary actions">
            <button type="button" class="btn btn--primary btn--lg" :disabled="isOpening" @click="openDemo">
              <LayoutDashboard aria-hidden="true" />
              {{ isOpening ? 'Opening demo…' : 'Try Dashboard' }}
            </button>
            <router-link to="/api-playground" class="btn">
              <Braces aria-hidden="true" />
              Try API Playground
            </router-link>
            <router-link to="/docs" class="btn btn--quiet">
              <FileCode2 aria-hidden="true" />
              Java Docs &amp; Coverage
            </router-link>
          </div>
          <p class="demo-note">
            Opens the demo account (<code>demo@finlab.app</code>) with sample trades already on the books. No password.
          </p>
        </div>

        <figure class="hero-plate">
          <img
            src="/images/hero-pad-1600.webp"
            srcset="/images/hero-pad-960.webp 960w, /images/hero-pad-1600.webp 1600w"
            sizes="(max-width: 900px) 100vw, 58vw"
            width="1600"
            height="1067"
            alt="A green engineering computation pad with two hand-plotted pencil curves, columns of figures, one negative value in red pencil and a final figure boxed in yellow highlighter, beside a mechanical pencil and a red pencil."
            fetchpriority="high"
          />
          <figcaption>Illustration: the working, by hand. The app does the same sums in Web Workers.</figcaption>
        </figure>
      </section>

      <section class="problem" aria-labelledby="problem-heading">
        <h2 id="problem-heading" class="sr-only">How FinLab works</h2>
        <dl class="problem-sheet">
          <div class="p-row">
            <dt>Given</dt>
            <dd>
              Daily AAPL and SPY candles (Jan–Apr 2024), a demo portfolio of sample trades, and your browser's
              IndexedDB. Mock mode needs no server.
            </dd>
          </div>
          <div class="p-row">
            <dt>Find</dt>
            <dd>Cost basis per holding, portfolio risk, and whether dollar-cost averaging beats a lump sum.</dd>
          </div>
          <div class="p-row p-solution">
            <dt id="feature-heading">Solution</dt>
            <dd>
              <ol class="steps" aria-labelledby="feature-heading">
                <li>
                  <h3><Wallet aria-hidden="true" />Portfolio Tracking</h3>
                  <p>FIFO cost basis with deterministic holdings reconciliation and IndexedDB persistence.</p>
                </li>
                <li>
                  <h3><FlaskConical aria-hidden="true" />DCA Backtesting</h3>
                  <p>10k+ OHLCV candles powered by dedicated Web Workers for smooth simulations.</p>
                </li>
                <li>
                  <h3><Activity aria-hidden="true" />Risk Metrics</h3>
                  <p>Sharpe ratio, max drawdown, and volatility calculated off-thread for reliability.</p>
                </li>
                <li>
                  <h3><Cable aria-hidden="true" />OpenAPI Contract</h3>
                  <p>Swagger-backed playground with mock/live toggle and RBAC-aware handlers.</p>
                </li>
              </ol>
            </dd>
          </div>
          <div class="p-row p-answer">
            <dt>Answer</dt>
            <dd>
              <button type="button" class="answer answer-btn" :disabled="isOpening" @click="openDemo">
                Open the demo portfolio
                <ArrowRight aria-hidden="true" />
              </button>
            </dd>
          </div>
        </dl>
      </section>

      <section class="mode-switcher" aria-labelledby="mode-heading">
        <div class="block-head">
          <h2 id="mode-heading">Backend Mode</h2>
          <p>Switch between a deterministic mock API and a live Spring Boot backend deployment.</p>
        </div>
        <div v-if="configStore.isDemoOnly" class="mode-body">
          <p class="mode-hint">
            이 공개 데모는 브라우저에 저장된 2024년 샘플 데이터(Mock)로만 동작합니다. 실제 시세·계좌와 연결되지 않으며 Live 백엔드 연결은 꺼져 있습니다.
          </p>
        </div>
        <div v-else class="mode-body">
          <div class="mode-actions" role="group" aria-label="Backend mode">
            <button type="button" class="seg" :aria-pressed="isMockMode" @click="activateMockMode">Mock</button>
            <button
              type="button"
              class="seg"
              :aria-pressed="!isMockMode"
              :disabled="!backendUrl"
              @click="activateLiveMode"
            >
              Live (Connect)
            </button>
          </div>
          <div class="field mode-url">
            <label for="landing-backend-url">Live URL</label>
            <input id="landing-backend-url" v-model="backendUrl" type="url" placeholder="https://finlab-api.fly.dev" />
          </div>
          <p class="mode-hint">Mock 모드는 오프라인에서도 완전하게 동작합니다.</p>
        </div>
      </section>

      <footer class="disclaimer">
        <p>
          Educational simulation. No real money. Java backend optional.
          <router-link to="/docs" class="footer-link">(deploy guide)</router-link>
        </p>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  LayoutDashboard,
  Braces,
  FileCode2,
  Wallet,
  FlaskConical,
  Activity,
  Cable,
  ArrowRight
} from 'lucide-vue-next'
import { useConfigStore } from '@/stores/configStore'
import { useAuthStore } from '@/stores/authStore'
import { useToast } from '@/composables/useToast'
import { DEMO_EMAIL } from '@/utils/seedLoader'

// Landing page - 프로젝트 소개 및 CTA
const router = useRouter()
const configStore = useConfigStore()
const authStore = useAuthStore()
const toast = useToast()

const isOpening = ref(false)
const isAuthenticated = computed(() => authStore.isAuthenticated)
const isMockMode = computed(() => configStore.isMockMode)
const backendUrl = computed({
  get: () => configStore.backendUrl,
  set: (value) => configStore.setBackendUrl(value.trim())
})

/**
 * 원클릭 데모: 로그인되어 있지 않으면 데모 계정으로 로그인한 뒤 대시보드로 이동
 */
async function openDemo() {
  if (isOpening.value) return
  if (!authStore.isAuthenticated) {
    isOpening.value = true
    const ok = await authStore.login(DEMO_EMAIL)
    isOpening.value = false
    if (!ok) {
      toast.error(authStore.error ?? '데모 로그인에 실패했습니다.')
      router.push('/login')
      return
    }
  }
  router.push('/dashboard')
}

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
.landing {
  min-height: 100vh;
}

.landing-frame {
  max-width: var(--page-max);
  margin: 0 auto;
  padding: var(--space-6) var(--space-6) var(--space-12);
}

/* ---------- cover title block ---------- */
.cover-block {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  border: 1.5px solid var(--rule-strong);
  font-size: var(--text-sm);
}

.cb-cell {
  padding: var(--space-2) var(--space-4);
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.cb-cell + .cb-cell {
  border-left: 1.5px solid var(--rule-strong);
}

.cb-key {
  font-family: var(--font-label);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: var(--tracking-label);
  text-transform: uppercase;
  color: var(--ink-2);
}

.cb-brand {
  flex-direction: row;
  align-items: center;
  gap: var(--space-2);
  font-family: var(--font-label);
  font-weight: 700;
  font-size: var(--text-xl);
  text-transform: uppercase;
  background-color: var(--binding);
  color: var(--binding-ink);
}

.cb-mark {
  width: 24px;
  height: 24px;
}

.cb-links {
  flex-direction: row;
  align-items: center;
  gap: var(--space-5);
}

.cb-links a {
  font-weight: 600;
  font-size: var(--text-base);
  color: var(--ink);
  text-decoration: none;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
}

.cb-links a:hover {
  text-decoration: underline;
}

/* ---------- hero ---------- */
.hero {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  gap: var(--space-10);
  align-items: center;
  padding: var(--space-12) 0 var(--space-16);
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.hero-title {
  font-size: var(--text-display);
  font-weight: 700;
  line-height: 0.85;
  letter-spacing: -0.01em;
  text-transform: uppercase;
}

.tagline {
  margin: 0;
  font-size: var(--text-xl);
  line-height: 1.35;
  color: var(--ink);
  max-width: 30ch;
}

.cta-buttons {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.btn--lg {
  min-height: 52px;
  padding: 0 var(--space-6);
  font-size: var(--text-lg);
}

.demo-note {
  margin: 0;
  max-width: 44ch;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--ink-2);
}

.hero-plate {
  margin: 0;
}

.hero-plate img {
  width: 100%;
  height: auto;
  border: 1.5px solid var(--rule-strong);
  transform: rotate(0.6deg);
}

.hero-plate figcaption {
  margin-top: var(--space-4);
  font-size: var(--text-sm);
  color: var(--ink-2);
}

/* ---------- given / find / solution / answer ---------- */
.problem-sheet {
  margin: 0;
  border-top: 2px solid var(--rule-strong);
}

.p-row {
  display: grid;
  grid-template-columns: 10rem minmax(0, 1fr);
  gap: var(--space-6);
  padding: var(--space-6) 0;
  border-bottom: 1px solid var(--rule);
}

.p-row dt {
  font-family: var(--font-label);
  font-weight: 700;
  font-size: var(--text-xl);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.p-row dd {
  margin: 0;
  font-size: var(--text-lg);
  max-width: var(--measure);
}

.steps {
  list-style: none;
  counter-reset: step;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-6) var(--space-10);
}

.steps li {
  counter-increment: step;
  position: relative;
  padding-left: var(--space-10);
}

.steps li::before {
  content: '(' counter(step, lower-alpha) ')';
  position: absolute;
  left: 0;
  top: 0.15em;
  font-family: var(--font-figure);
  font-size: var(--text-xs);
  color: var(--ink-2);
}

.steps h3 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-xl);
  text-transform: uppercase;
  letter-spacing: 0.03em;
  margin-bottom: var(--space-2);
}

.steps h3 svg {
  width: 20px;
  height: 20px;
  flex: none;
}

.steps p {
  margin: 0;
  font-size: var(--text-base);
  line-height: 1.6;
  color: var(--ink-2);
}

.p-solution {
  max-width: none;
}

.p-solution dd {
  max-width: none;
}

.p-answer {
  border-bottom: 3px double var(--rule-strong);
}

.answer-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 52px;
  padding: 0 var(--space-5);
  font-family: var(--font-label);
  font-size: var(--text-xl);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  cursor: pointer;
  transition: box-shadow var(--dur-base) var(--ease-out);
}

.answer-btn:hover {
  box-shadow: inset 0 -100px 0 var(--highlight);
}

.answer-btn svg {
  width: 22px;
  height: 22px;
}

/* ---------- backend mode ---------- */
.mode-switcher {
  margin-top: var(--space-16);
}

.mode-body {
  display: grid;
  grid-template-columns: auto minmax(0, 28rem) minmax(0, 1fr);
  gap: var(--space-6);
  align-items: end;
}

.mode-actions {
  display: inline-flex;
  border: 1.5px solid var(--rule-strong);
}

.seg {
  min-height: 44px;
  padding: 0 var(--space-5);
  font-weight: 600;
  font-size: var(--text-base);
  color: var(--ink);
}

.seg + .seg {
  border-left: 1.5px solid var(--rule-strong);
}

.seg[aria-pressed='true'] {
  background-color: var(--ink);
  color: var(--paper);
}

.seg:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.mode-hint {
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--ink-2);
}

.disclaimer {
  margin-top: var(--space-16);
  padding-top: var(--space-4);
  border-top: 1px solid var(--rule);
  font-size: var(--text-sm);
  color: var(--ink-2);
}

.disclaimer p {
  margin: 0;
}

.footer-link {
  margin-left: var(--space-1);
}

/* ---------- responsive ---------- */
@media (max-width: 1000px) {
  .hero {
    grid-template-columns: minmax(0, 1fr);
    padding: var(--space-8) 0 var(--space-12);
  }

  .mode-body {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
  }
}

@media (max-width: 720px) {
  .landing-frame {
    padding: var(--space-3) var(--space-4) var(--space-10);
  }

  .cover-block {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .cb-sheet {
    display: none;
  }

  .cb-links {
    grid-column: 1 / -1;
    border-left: 0 !important;
    border-top: 1.5px solid var(--rule-strong);
    justify-content: space-between;
  }

  .hero {
    gap: var(--space-8);
  }

  .hero-title {
    font-size: clamp(4rem, 22vw, 6rem);
  }

  .tagline {
    font-size: var(--text-lg);
  }

  .cta-buttons .btn {
    flex: 1 1 100%;
  }

  .p-row {
    grid-template-columns: 1fr;
    gap: var(--space-2);
    padding: var(--space-5) 0;
  }

  .p-row dd {
    font-size: var(--text-base);
  }

  .steps {
    grid-template-columns: 1fr;
  }

  .answer-btn {
    width: 100%;
    justify-content: space-between;
    font-size: var(--text-lg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-plate img {
    transform: none;
  }
}
</style>
