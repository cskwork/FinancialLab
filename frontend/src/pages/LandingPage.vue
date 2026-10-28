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
          <span class="cb-key">{{ t('pad.subject') }}</span>
          <span>{{ t('landing.subjectValue') }}</span>
        </div>
        <div class="cb-cell cb-sheet">
          <span class="cb-key">{{ t('pad.sheet') }}</span>
          <span class="fig">{{ t('landing.sheetValue') }}</span>
        </div>
        <div class="cb-cell cb-links">
          <nav class="cb-nav" :aria-label="t('landing.coverLinks')">
            <router-link to="/api-playground">{{ t('nav.apiPlayground') }}</router-link>
            <router-link to="/docs">{{ t('nav.docs') }}</router-link>
            <router-link v-if="!isAuthenticated" to="/login">{{ t('common.login') }}</router-link>
          </nav>
          <LocaleSelect id="landing-language" compact />
        </div>
      </header>

      <section class="hero" aria-labelledby="hero-title">
        <div class="hero-copy">
          <h1 id="hero-title" class="hero-title">FinLab</h1>
          <p class="tagline">{{ t('landing.tagline') }}</p>
          <div class="cta-buttons" role="group" :aria-label="t('landing.primaryActions')">
            <button type="button" class="btn btn--primary btn--lg" :disabled="isOpening" @click="openDemo">
              <LayoutDashboard aria-hidden="true" />
              {{ isOpening ? t('landing.openingDemo') : t('landing.tryDashboard') }}
            </button>
            <router-link to="/api-playground" class="btn">
              <Braces aria-hidden="true" />
              {{ t('landing.tryApi') }}
            </router-link>
            <router-link to="/docs" class="btn btn--quiet">
              <FileCode2 aria-hidden="true" />
              {{ t('landing.javaDocs') }}
            </router-link>
          </div>
          <i18n-t scope="global" keypath="landing.demoNote" tag="p" class="demo-note">
            <template #email><code>{{ DEMO_EMAIL }}</code></template>
          </i18n-t>
        </div>

        <figure class="hero-plate">
          <img
            src="/images/hero-pad-1600.webp"
            srcset="/images/hero-pad-960.webp 960w, /images/hero-pad-1600.webp 1600w"
            sizes="(max-width: 900px) 100vw, 58vw"
            width="1600"
            height="1067"
            :alt="t('landing.heroAlt')"
            fetchpriority="high"
          />
          <figcaption>{{ t('landing.heroCaption') }}</figcaption>
        </figure>
      </section>

      <section class="problem" aria-labelledby="problem-heading">
        <h2 id="problem-heading" class="sr-only">{{ t('landing.howItWorks') }}</h2>
        <dl class="problem-sheet">
          <div class="p-row">
            <dt>{{ t('landing.given') }}</dt>
            <dd>{{ t('landing.givenBody') }}</dd>
          </div>
          <div class="p-row">
            <dt>{{ t('landing.find') }}</dt>
            <dd>{{ t('landing.findBody') }}</dd>
          </div>
          <div class="p-row p-solution">
            <dt id="feature-heading">{{ t('landing.solution') }}</dt>
            <dd>
              <ol class="steps" aria-labelledby="feature-heading">
                <li>
                  <h3><Wallet aria-hidden="true" />{{ t('landing.stepPortfolioTitle') }}</h3>
                  <p>{{ t('landing.stepPortfolioBody') }}</p>
                </li>
                <li>
                  <h3><FlaskConical aria-hidden="true" />{{ t('landing.stepBacktestTitle') }}</h3>
                  <p>{{ t('landing.stepBacktestBody') }}</p>
                </li>
                <li>
                  <h3><Activity aria-hidden="true" />{{ t('landing.stepRiskTitle') }}</h3>
                  <p>{{ t('landing.stepRiskBody') }}</p>
                </li>
                <li>
                  <h3><Cable aria-hidden="true" />{{ t('landing.stepApiTitle') }}</h3>
                  <p>{{ t('landing.stepApiBody') }}</p>
                </li>
              </ol>
            </dd>
          </div>
          <div class="p-row p-answer">
            <dt>{{ t('landing.answer') }}</dt>
            <dd>
              <button type="button" class="answer answer-btn" :disabled="isOpening" @click="openDemo">
                {{ t('landing.openDemo') }}
                <ArrowRight aria-hidden="true" />
              </button>
            </dd>
          </div>
        </dl>
      </section>

      <section class="mode-switcher" aria-labelledby="mode-heading">
        <div class="block-head">
          <h2 id="mode-heading">{{ t('landing.backendMode') }}</h2>
          <p>{{ t('landing.backendModeBody') }}</p>
        </div>
        <div v-if="configStore.isDemoOnly" class="mode-body">
          <p class="mode-hint">{{ t('landing.demoOnlyHint') }}</p>
        </div>
        <div v-else class="mode-body">
          <div class="mode-actions" role="group" :aria-label="t('landing.modeGroup')">
            <button type="button" class="seg" :aria-pressed="isMockMode" @click="activateMockMode">{{ t('nav.mock') }}</button>
            <button
              type="button"
              class="seg"
              :aria-pressed="!isMockMode"
              :disabled="!backendUrl"
              @click="activateLiveMode"
            >
              {{ t('landing.liveConnect') }}
            </button>
          </div>
          <div class="field mode-url">
            <label for="landing-backend-url">{{ t('landing.liveUrl') }}</label>
            <input id="landing-backend-url" v-model="backendUrl" type="url" placeholder="http://localhost:8080" />
          </div>
          <p class="mode-hint">{{ t('landing.mockHint') }}</p>
        </div>
      </section>

      <footer class="disclaimer">
        <p>
          {{ t('landing.footer') }}
          <router-link to="/docs" class="footer-link">{{ t('landing.deployGuide') }}</router-link>
        </p>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
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
import { localizeError } from '@/utils/localizedMessages'
import LocaleSelect from '@/components/common/LocaleSelect.vue'

// Landing page - 프로젝트 소개 및 CTA
const router = useRouter()
const { t } = useI18n()
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
      toast.error(authStore.error ? localizeError(authStore.error) : t('landing.demoLoginFailed'))
      router.push('/login')
      return
    }
  }
  router.push('/dashboard')
}

function activateMockMode() {
  configStore.setBackendMode('mock')
  toast.info(t('nav.mockModeEnabled'))
}

function activateLiveMode() {
  if (!backendUrl.value) {
    toast.warning(t('landing.liveUrlRetry'))
    return
  }
  configStore.setBackendMode('live')
  toast.success(t('landing.liveConnecting'))
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

.cb-nav {
  display: flex;
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
    flex-wrap: wrap;
    row-gap: var(--space-1);
  }

  .cb-nav {
    flex-wrap: wrap;
    column-gap: var(--space-4);
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
