import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/tokens.css'
import './styles/global.css'
import App from './App.vue'
import router from './app/router'
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

app.mount('#app')

// 데이터베이스 초기화 및 seed 데이터 로드
initializeSeedData().catch((error) => {
  console.error('Failed to initialize seed data:', error)
})
