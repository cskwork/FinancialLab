import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { watch } from 'vue'
import './styles/tokens.css'
import './styles/global.css'
import App from './App.vue'
import router from './app/router'
import i18n from './i18n'
import { initializeSeedData } from './utils/seedLoader'
import { useAuthStore } from './stores/authStore'
import { useConfigStore } from './stores/configStore'

const app = createApp(App)

// Pinia 등록 (상태 관리)
const pinia = createPinia()
app.use(pinia)

// 전역 스토어 상태 복원
const authStore = useAuthStore()
authStore.restoreSession()

const configStore = useConfigStore()
configStore.restore()

// Vue Router 등록
app.use(router)

// i18n 등록
app.use(i18n)

// i18n과 configStore의 locale 동기화
// @ts-ignore - vue-i18n 타입 이슈
i18n.global.locale.value = configStore.locale

// configStore의 locale 변경을 감시하여 i18n과 동기화
watch(() => configStore.locale, (newLocale) => {
  // @ts-ignore - vue-i18n 타입 이슈
  i18n.global.locale.value = newLocale
})

app.mount('#app')

// 데이터베이스 초기화 및 seed 데이터 로드
initializeSeedData().catch((error) => {
  console.error('Failed to initialize seed data:', error)
})
