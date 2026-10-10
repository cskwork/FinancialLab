<template>
  <nav class="binding" :aria-label="t('nav.primary')">
    <div class="binding-inner">
      <router-link to="/" class="brand" @click="closeMenu">
        <svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true">
          <rect x="1" y="1" width="30" height="30" fill="none" stroke="currentColor" stroke-width="2" />
          <path d="M5 24 L11 17 L16 20 L27 7" fill="none" stroke="currentColor" stroke-width="2.6" />
          <path d="M5 27 H27" stroke="var(--highlight)" stroke-width="3" />
        </svg>
        <span class="brand-name">FinLab</span>
      </router-link>

      <button
        type="button"
        class="menu-toggle"
        :aria-expanded="menuOpen"
        aria-controls="nav-drawer"
        @click="menuOpen = !menuOpen"
      >
        <component :is="menuOpen ? X : Menu" aria-hidden="true" />
        <span>{{ t('nav.menu') }}</span>
      </button>

      <div id="nav-drawer" class="drawer" :class="{ open: menuOpen }">
        <ul v-if="authStore.isAuthenticated" class="sheets">
          <li v-for="item in sheets" :key="item.to">
            <router-link :to="item.to" class="sheet-tab" active-class="active" @click="closeMenu">
              <span class="sheet-no fig" aria-hidden="true">{{ item.no }}</span>
              <span>{{ t(item.label) }}</span>
            </router-link>
          </li>
        </ul>
        <ul class="appendix">
          <li>
            <router-link to="/api-playground" class="appendix-link" active-class="active" @click="closeMenu">
              {{ t('nav.apiPlayground') }}
            </router-link>
          </li>
          <li>
            <router-link to="/docs" class="appendix-link" active-class="active" @click="closeMenu">
              {{ t('nav.docs') }}
            </router-link>
          </li>
        </ul>

        <div class="tools">
          <details ref="settingsRef" class="settings">
            <summary class="settings-summary">
              <Settings2 aria-hidden="true" />
              <span>{{ t('nav.settings') }}</span>
              <span class="mode-chip" :class="{ live: backendMode === 'live' }">
                {{ backendMode === 'mock' ? t('nav.mock') : t('nav.live') }}
              </span>
            </summary>
            <div class="settings-panel">
              <LocaleSelect />
              <p v-if="configStore.isDemoOnly" class="hint">{{ t('nav.demoOnlyHint') }}</p>
              <template v-else>
                <div class="field">
                  <label for="backend-mode">{{ t('nav.backend') }}</label>
                  <select id="backend-mode" v-model="backendMode" aria-describedby="backend-mode-hint">
                    <option value="mock">{{ t('nav.mock') }}</option>
                    <option value="live">{{ t('nav.live') }}</option>
                  </select>
                </div>
                <div class="field">
                  <label for="backend-url">{{ t('nav.backendUrl') }}</label>
                  <input
                    id="backend-url"
                    v-model="backendUrl"
                    type="url"
                    :disabled="backendMode === 'mock'"
                    placeholder="https://finlab-api.fly.dev"
                  />
                </div>
                <button
                  type="button"
                  class="btn btn--sm"
                  :disabled="backendMode === 'mock'"
                  @click="handleConnectLive"
                >
                  <PlugZap aria-hidden="true" />
                  {{ t('common.connect') }}
                </button>
                <p id="backend-mode-hint" class="hint">{{ t('nav.backendHint') }}</p>
              </template>
            </div>
          </details>

          <span v-if="authStore.isAuthenticated" class="user-email truncate">
            {{ authStore.currentUserEmail }}
          </span>
          <button v-if="authStore.isAuthenticated" type="button" class="bind-btn" @click="handleLogout">
            <LogOut aria-hidden="true" />
            {{ t('common.logout') }}
          </button>
          <router-link v-else to="/login" class="bind-btn" @click="closeMenu">
            <LogIn aria-hidden="true" />
            {{ t('common.login') }}
          </router-link>
        </div>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Menu, X, Settings2, PlugZap, LogOut, LogIn } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/authStore'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { useTransactionStore } from '@/stores/transactionStore'
import { useGoalPlanStore } from '@/stores/goalPlanStore'
import { useAnalyticsStore } from '@/stores/analyticsStore'
import { useConfigStore, type BackendMode } from '@/stores/configStore'
import { useToast } from '@/composables/useToast'
import LocaleSelect from './LocaleSelect.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const transactionStore = useTransactionStore()
const goalPlanStore = useGoalPlanStore()
const analyticsStore = useAnalyticsStore()
const configStore = useConfigStore()
const toast = useToast()

const menuOpen = ref(false)
const settingsRef = ref<HTMLDetailsElement | null>(null)

const sheets = [
  { to: '/dashboard', no: '01', label: 'nav.dashboard' },
  { to: '/transactions', no: '02', label: 'nav.transactions' },
  { to: '/goals-plans', no: '03', label: 'nav.goalsPlans' },
  { to: '/backtest', no: '04', label: 'nav.backtestLab' }
]

const backendMode = computed({
  get: () => configStore.backendMode,
  set: (value: BackendMode) => {
    configStore.setBackendMode(value)
    toast.info(value === 'mock' ? t('nav.mockModeEnabled') : t('nav.liveModeEnabled'))
  }
})

const backendUrl = computed({
  get: () => configStore.backendUrl,
  set: (value) => configStore.setBackendUrl(value.trim())
})

function closeMenu() {
  menuOpen.value = false
}

function closeSettings() {
  if (settingsRef.value) settingsRef.value.open = false
}

// 로그아웃 처리
function handleLogout() {
  authStore.logout()
  portfolioStore.reset()
  transactionStore.reset()
  goalPlanStore.reset()
  analyticsStore.reset()
  closeMenu()
  router.push('/login')
}

function handleConnectLive() {
  if (!backendUrl.value) {
    toast.warning(t('nav.liveUrlRequired'))
    return
  }

  configStore.setBackendMode('live')
  toast.success(t('nav.liveConnecting'))
}

function handleDocumentClick(event: MouseEvent) {
  const el = settingsRef.value
  if (el?.open && !el.contains(event.target as Node)) closeSettings()
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (settingsRef.value?.open) {
    closeSettings()
    settingsRef.value.querySelector('summary')?.focus()
  } else if (menuOpen.value) {
    closeMenu()
  }
}

watch(
  () => route.fullPath,
  () => {
    closeMenu()
    closeSettings()
  }
)

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.binding {
  position: sticky;
  top: 0;
  z-index: 100;
  background-color: var(--binding);
  color: var(--binding-ink);
  border-bottom: 3px solid var(--rule-strong);
}

.binding-inner {
  max-width: var(--page-max);
  margin: 0 auto;
  padding: 0 var(--space-6);
  min-height: 60px;
  display: flex;
  align-items: stretch;
  gap: var(--space-6);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--binding-ink);
  text-decoration: none;
  font-family: var(--font-label);
  font-weight: 700;
  font-size: 1.5rem;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

.brand-mark {
  width: 26px;
  height: 26px;
}

.menu-toggle {
  display: none;
}

.drawer {
  flex: 1;
  display: flex;
  align-items: stretch;
  gap: var(--space-6);
  min-width: 0;
}

.sheets,
.appendix {
  list-style: none;
  display: flex;
  align-items: stretch;
}

.sheets li,
.appendix li {
  display: flex;
}

.sheet-tab {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-4);
  margin-bottom: -3px;
  color: var(--binding-muted);
  text-decoration: none;
  font-size: var(--text-base);
  font-weight: 600;
  border: 1.5px solid transparent;
  border-bottom: 0;
  transition: color var(--dur-fast) var(--ease-out), background-color var(--dur-fast) var(--ease-out);
  white-space: nowrap;
}

.sheet-no {
  font-size: var(--text-xs);
}

.sheet-tab:hover {
  color: var(--binding-ink);
}

/* the active sheet is torn to the front: it takes the paper's colour and joins the page */
.sheet-tab.active {
  background-color: var(--paper);
  color: var(--ink);
  border-color: var(--rule-strong);
}

.appendix {
  gap: var(--space-4);
}

.appendix-link {
  display: inline-flex;
  align-items: center;
  color: var(--binding-muted);
  font-size: var(--text-base);
  text-decoration: none;
  white-space: nowrap;
}

.appendix-link:hover,
.appendix-link.active {
  color: var(--binding-ink);
  text-decoration: underline;
  text-underline-offset: 0.3em;
}

.tools {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--space-4);
  min-width: 0;
}

.settings {
  position: relative;
}

.settings-summary {
  list-style: none;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 44px;
  padding: 0 var(--space-3);
  color: var(--binding-ink);
  font-size: var(--text-base);
  cursor: pointer;
  border: 1px solid color-mix(in srgb, var(--binding-ink) 30%, transparent);
  white-space: nowrap;
}

.settings-summary::-webkit-details-marker {
  display: none;
}

.settings-summary svg,
.bind-btn svg,
.menu-toggle svg {
  width: 18px;
  height: 18px;
}

.settings[open] .settings-summary {
  background-color: var(--paper);
  color: var(--ink);
}

.mode-chip {
  font-family: var(--font-label);
  font-weight: 700;
  letter-spacing: var(--tracking-label);
  text-transform: uppercase;
  font-size: var(--text-xs);
  padding: 1px 6px;
  border: 1px solid currentColor;
}

.mode-chip.live {
  background-color: var(--highlight);
  color: var(--highlight-ink);
  border-color: var(--highlight);
}

.settings-panel {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  width: min(320px, 90vw);
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  background-color: var(--paper);
  color: var(--ink);
  border: 1.5px solid var(--rule-strong);
  box-shadow: var(--shadow-lg);
  z-index: 10;
}

.hint {
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.5;
  color: var(--ink-2);
}

.user-email {
  max-width: 14rem;
  font-size: var(--text-xs);
  color: var(--binding-muted);
}

.bind-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 44px;
  padding: 0 var(--space-3);
  color: var(--binding-ink);
  font-size: var(--text-base);
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  border: 1px solid color-mix(in srgb, var(--binding-ink) 30%, transparent);
}

.bind-btn:hover,
.settings-summary:hover {
  border-color: var(--binding-ink);
  text-decoration: none;
}

.binding :focus-visible {
  outline-color: var(--highlight);
}

/* the unfolded strip is capped at --page-max and has no room left for the address */
@media (min-width: 1321px) {
  .user-email {
    display: none;
  }
}

/* the full English strip needs ~1270px, so fold anywhere the page is narrower than --page-max */
@media (max-width: 1320px) {
  .binding-inner {
    flex-wrap: wrap;
    padding: 0 var(--space-4);
    gap: 0;
    justify-content: space-between;
  }

  .menu-toggle {
    display: inline-flex;
    align-items: center;
    gap: var(--space-2);
    align-self: center;
    min-height: 44px;
    padding: 0 var(--space-3);
    color: var(--binding-ink);
    font-weight: 600;
    border: 1px solid color-mix(in srgb, var(--binding-ink) 30%, transparent);
  }

  .drawer {
    display: none;
    flex-basis: 100%;
    flex-direction: column;
    gap: var(--space-4);
    padding: var(--space-2) 0 var(--space-5);
  }

  .drawer.open {
    display: flex;
  }

  .sheets {
    flex-direction: column;
  }

  .sheet-tab {
    min-height: 48px;
    width: 100%;
    margin-bottom: 0;
    padding: 0 var(--space-3);
    border-bottom: 1px solid color-mix(in srgb, var(--binding-ink) 18%, transparent);
  }

  .sheet-tab.active {
    border-bottom-color: var(--rule-strong);
  }

  .appendix {
    gap: var(--space-5);
    padding: 0 var(--space-3);
  }

  .appendix-link {
    min-height: 44px;
  }

  .tools {
    margin-left: 0;
    flex-wrap: wrap;
    padding: 0 var(--space-3);
  }

  .settings {
    width: 100%;
  }

  .settings-summary {
    min-height: 44px;
  }

  .settings-panel {
    position: static;
    width: 100%;
    margin-top: var(--space-2);
    box-shadow: none;
  }

  .bind-btn {
    min-height: 44px;
  }
}
</style>
