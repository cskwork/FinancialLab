import { logger } from './utils/logger'

// 브라우저 확장 프로그램 충돌 방지
if (typeof window !== 'undefined') {
  // 잠재적 충돌 변수 보호
  const protectedVars = ['Sft', 'hook', 'overrideMethod']
  protectedVars.forEach(varName => {
    if (typeof (window as any)[varName] !== 'undefined') {
      logger.warn(`Potential browser extension conflict detected with variable: ${varName}`)
    }
  })
}

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { watch } from 'vue'
import '@fontsource/barlow-condensed/500.css'
import '@fontsource/barlow-condensed/600.css'
import '@fontsource/barlow-condensed/700.css'
import '@fontsource/azeret-mono/400.css'
import '@fontsource/azeret-mono/600.css'
import './styles/tokens.css'
import './styles/global.css'
import App from './App.vue'
import router from './app/router'
import i18n from './i18n'
import { initializeSeedData } from './utils/seedLoader'
import { useAuthStore } from './stores/authStore'
import { useConfigStore } from './stores/configStore'

// 시작 단계 화면: Vue 마운트 전에 #app에 직접 그린다 (스타일 시트 없이도 읽히도록 인라인 여백만 지정)
function renderStartupScreen(html: string) {
  document.documentElement.lang = 'ko'
  const root = document.getElementById('app')
  if (root) root.innerHTML = html
  return root
}

function showStartupLoading() {
  renderStartupScreen(`<main class="page startup-loading" role="status" aria-live="polite" style="padding-top:15vh;max-width:48rem">
    <p>예시 데이터를 준비하는 중입니다… (2024년 샘플 가격, 브라우저에만 저장)</p>
  </main>`)
}

// 브라우저 저장소를 열지 못하면 앱을 시작하지 않고 기존 기록을 건드리지 않은 채 재시도를 안내한다
function showStartupError() {
  const root = renderStartupScreen(`<main class="page startup-error" role="alert" aria-labelledby="startup-title" style="padding-top:15vh;max-width:48rem">
    <h1 id="startup-title" style="margin-bottom:var(--space-6)">브라우저 저장 공간을 열지 못했습니다</h1>
    <p style="margin-bottom:var(--space-6)">기록을 안전하게 불러올 수 없어 데모를 시작하지 않았습니다. 이 사이트의 저장 권한과 사용 가능한 공간을 확인한 뒤 다시 시도하세요. 기존 기록은 삭제하지 않았습니다.</p>
    <button type="button" class="btn btn--primary" id="startup-retry">다시 시도</button>
  </main>`)
  root?.querySelector('#startup-retry')?.addEventListener('click', () => window.location.reload())
}

async function bootstrap() {
  const app = createApp(App)

  // Pinia 등록 (상태 관리)
  const pinia = createPinia()
  app.use(pinia)

  // 전역 스토어 상태 복원
  const authStore = useAuthStore()
  const configStore = useConfigStore()
  try {
    authStore.restoreSession()
    configStore.restore()
  } catch {
    showStartupError()
    return
  }

  // 데이터베이스 초기화 및 seed 데이터 로드: 라우터 첫 내비게이션과 대시보드 조회보다 먼저 끝나야 한다
  showStartupLoading()
  try {
    await initializeSeedData()
  } catch (error) {
    logger.error('Failed to initialize seed data', error)
    showStartupError()
    return
  }

  // Vue Router 등록
  app.use(router)

  // i18n 등록
  app.use(i18n)

  // i18n과 configStore의 locale 동기화
  // @ts-ignore - vue-i18n 타입 이슈
  i18n.global.locale.value = configStore.locale
  document.documentElement.lang = configStore.locale

  // configStore의 locale 변경을 감시하여 i18n과 동기화
  watch(() => configStore.locale, (newLocale) => {
    // @ts-ignore - vue-i18n 타입 이슈
    i18n.global.locale.value = newLocale
    document.documentElement.lang = newLocale
  })

  app.mount('#app')
}

void bootstrap()
