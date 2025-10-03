<template>
  <div class="playground-page">
    <header>
      <h1>API Playground</h1>
      <p class="intro">Inspect the OpenAPI contract and exercise mock or live endpoints directly from the browser.</p>
      <p class="mode-hint">Current backend mode: <strong>{{ configStore.backendMode.toUpperCase() }}</strong></p>
    </header>

    <section class="content">
      <aside class="sidebar">
        <h2>Endpoints</h2>
        <ul>
          <li
            v-for="operation in operations"
            :key="operation.operationId"
            :class="{ active: selectedOperation?.operationId === operation.operationId }"
          >
            <button type="button" @click="selectOperation(operation)">
              <span class="method" :class="operation.method">{{ operation.method }}</span>
              <span class="path">{{ operation.path }}</span>
            </button>
          </li>
        </ul>
      </aside>

      <main class="details" v-if="selectedOperation">
        <header class="details-header">
          <h2>{{ selectedOperation.summary }}</h2>
          <code>{{ selectedOperation.method.toUpperCase() }} {{ selectedOperation.path }}</code>
        </header>

        <section class="request-section">
          <h3>Request</h3>
          <div v-if="pathParamsKeys.length" class="param-grid">
            <label v-for="param in pathParamsKeys" :key="param">
              <span>{{ param }}</span>
              <input v-model="pathParams[param]" type="text" />
            </label>
          </div>

          <div v-if="selectedOperation.requiresBody" class="body-editor">
            <label>Request Body (JSON)</label>
            <textarea v-model="requestPayload" rows="10"></textarea>
          </div>

          <button class="btn-primary" :disabled="isExecuting" @click="execute">
            {{ isExecuting ? 'Executing...' : 'Execute' }}
          </button>
        </section>

        <section class="response-section" v-if="response">
          <h3>Response ({{ response.status }})</h3>
          <pre>{{ responseBody }}</pre>
        </section>
      </main>

      <main class="details" v-else>
        <p>Select an endpoint to view details and execute it.</p>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useConfigStore } from '@/stores/configStore'
import { useAuthStore } from '@/stores/authStore'
import { usePortfolioStore } from '@/stores/portfolioStore'
import { apiClient } from '@/services/api'
import { useToast } from '@/composables/useToast'

interface ApiOperation {
  method: string
  path: string
  summary: string
  operationId: string
  requiresBody: boolean
  example?: unknown
}

interface ApiResponsePayload {
  status: number
  body: unknown
}

const configStore = useConfigStore()
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const toast = useToast()

const operations = ref<ApiOperation[]>([])
const selectedOperation = ref<ApiOperation | null>(null)
const requestPayload = ref('{}')
const response = ref<ApiResponsePayload | null>(null)
const isExecuting = ref(false)
const pathParams = reactive<Record<string, string>>({})

const pathParamsKeys = computed(() => Object.keys(pathParams))
const responseBody = computed(() => (response.value ? JSON.stringify(response.value.body, null, 2) : ''))

async function loadSpec() {
  try {
    const res = await fetch('/docs/openapi.json')
    const spec = await res.json()
    const ops: ApiOperation[] = []

    Object.entries(spec.paths as Record<string, any>).forEach(([path, methods]) => {
      Object.entries(methods as Record<string, any>).forEach(([method, payload]) => {
        if (!payload.operationId) return
        ops.push({
          method: method.toUpperCase(),
          path,
          summary: payload.summary ?? payload.operationId,
          operationId: payload.operationId,
          requiresBody: Boolean(payload.requestBody),
          example: payload.requestBody?.content?.['application/json']?.example
        })
      })
    })

    operations.value = ops
  } catch (error) {
    toast.error('Failed to load OpenAPI spec.')
    console.error(error)
  }
}

function selectOperation(operation: ApiOperation) {
  selectedOperation.value = operation
  response.value = null
  requestPayload.value = operation.example ? JSON.stringify(operation.example, null, 2) : '{}'
  resetPathParams(operation)
}

function resetPathParams(operation: ApiOperation) {
  Object.keys(pathParams).forEach((key) => delete pathParams[key])
  const matches = operation.path.matchAll(/\{(.*?)\}/g)
  for (const match of matches) {
    if (match[1]) {
      pathParams[match[1]] = match[1] === 'id' && portfolioStore.currentPortfolio
        ? String(portfolioStore.currentPortfolio.id)
        : ''
    }
  }
}

async function execute() {
  if (!selectedOperation.value) return

  const operation = selectedOperation.value
  try {
    isExecuting.value = true
    const path = buildPath(operation.path)
    let payload: unknown = undefined

    if (operation.requiresBody) {
      try {
        payload = JSON.parse(requestPayload.value)
      } catch (err) {
        toast.error('Request body must be valid JSON.')
        return
      }
    }

    let result: ApiResponsePayload
    if (configStore.isMockMode) {
      result = await executeMockOperation(operation.operationId, payload)
    } else {
      result = await executeLiveOperation(operation.method, path, payload)
    }
    response.value = result
  } catch (error) {
    toast.error(error instanceof Error ? error.message : 'Request failed')
  } finally {
    isExecuting.value = false
  }
}

function buildPath(path: string) {
  let finalPath = path
  Object.entries(pathParams).forEach(([key, value]) => {
    finalPath = finalPath.replace(`{${key}}`, value || '')
  })
  return finalPath
}

async function executeMockOperation(operationId: string, body: unknown): Promise<ApiResponsePayload> {
  switch (operationId) {
    case 'login': {
      const response = await apiClient.login(body as { email: string })
      if (!response.success) throw new Error(response.error?.message ?? 'Login failed')
      return { status: 200, body: response.data }
    }
    case 'listPortfolios': {
      await ensureSession()
      const response = await apiClient.getPortfolios(authStore.currentUserId!, authStore.token)
      if (!response.success) throw new Error(response.error?.message ?? 'Failed to list portfolios')
      return { status: 200, body: response.data }
    }
    case 'createPortfolio': {
      await ensureSession()
      const response = await apiClient.createPortfolio(authStore.currentUserId!, body as any, authStore.token)
      if (!response.success) throw new Error(response.error?.message ?? 'Failed to create portfolio')
      return { status: 201, body: response.data }
    }
    case 'listTransactions': {
      await ensureSession()
      const portfolioId = Number(pathParams.id)
      if (!portfolioId) throw new Error('Provide a portfolio id.')
      const response = await apiClient.getTransactions(portfolioId, authStore.token)
      if (!response.success) throw new Error(response.error?.message ?? 'Failed to list transactions')
      return { status: 200, body: response.data }
    }
    case 'createTransaction': {
      await ensureSession()
      const portfolioId = Number(pathParams.id)
      if (!portfolioId) throw new Error('Provide a portfolio id.')
      const response = await apiClient.createTransaction(
        { ...(body as Record<string, unknown>), portfolioId } as any,
        authStore.token
      )
      if (!response.success) throw new Error(response.error?.message ?? 'Failed to create transaction')
      return { status: 201, body: response.data }
    }
    case 'getHoldings': {
      await ensureSession()
      const portfolioId = Number(pathParams.id)
      if (!portfolioId) throw new Error('Provide a portfolio id.')
      const response = await apiClient.getHoldings(portfolioId, authStore.token)
      if (!response.success) throw new Error(response.error?.message ?? 'Failed to load holdings')
      return { status: 200, body: response.data }
    }
    case 'getPortfolioMetrics': {
      await ensureSession()
      const portfolioId = Number(pathParams.id)
      if (!portfolioId) throw new Error('Provide a portfolio id.')
      const response = await apiClient.getPortfolioMetrics(portfolioId, authStore.token)
      if (!response.success) throw new Error(response.error?.message ?? 'Failed to load metrics')
      return { status: 200, body: response.data }
    }
    case 'listGoals':
    case 'createGoal': {
      await ensureSession()
      const portfolioId = Number(pathParams.id)
      if (operationId === 'listGoals') {
        const response = await apiClient.getGoals(portfolioId, authStore.token)
        if (!response.success) throw new Error(response.error?.message ?? 'Failed to load goals')
        return { status: 200, body: response.data }
      }
      const response = await apiClient.createGoal(
        { ...(body as Record<string, unknown>), portfolioId } as any,
        authStore.token
      )
      if (!response.success) throw new Error(response.error?.message ?? 'Failed to create goal')
      return { status: 201, body: response.data }
    }
    case 'listPlans':
    case 'createPlan': {
      await ensureSession()
      const portfolioId = Number(pathParams.id)
      if (operationId === 'listPlans') {
        const response = await apiClient.getPlans(portfolioId, authStore.token)
        if (!response.success) throw new Error(response.error?.message ?? 'Failed to load plans')
        return { status: 200, body: response.data }
      }
      const response = await apiClient.createPlan(
        { ...(body as Record<string, unknown>), portfolioId } as any,
        authStore.token
      )
      if (!response.success) throw new Error(response.error?.message ?? 'Failed to create plan')
      return { status: 201, body: response.data }
    }
    default:
      throw new Error(`Operation ${operationId} is not supported in mock mode yet.`)
  }
}

async function executeLiveOperation(method: string, path: string, body: unknown): Promise<ApiResponsePayload> {
  const baseUrl = configStore.backendUrl.replace(/\/$/, '')
  if (!baseUrl) throw new Error('Backend URL is not configured.')

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(authStore.token ? { Authorization: `Bearer ${authStore.token}` } : {})
    },
    body: method === 'GET' ? undefined : JSON.stringify(body)
  })

  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(payload?.message ?? `Live request failed with status ${response.status}`)
  }

  return {
    status: response.status,
    body: payload
  }
}

async function ensureSession() {
  if (!authStore.isAuthenticated) {
    throw new Error('Login first via the auth endpoint to obtain a session.')
  }
}

onMounted(() => {
  loadSpec()
})
</script>

<style scoped>
.playground-page {
  min-height: 100vh;
  padding: var(--space-6);
  background-color: var(--color-gray-50);
}

header {
  margin-bottom: var(--space-6);
}

header h1 {
  margin: 0;
  font-size: var(--font-size-3xl);
}

.intro {
  color: var(--color-text-muted);
}

.mode-hint {
  margin-top: var(--space-2);
  color: var(--color-primary-600);
}

.content {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--space-4);
}

.sidebar {
  background-color: var(--color-background);
  border-radius: var(--border-radius-xl);
  box-shadow: var(--shadow-sm);
  padding: var(--space-4);
  overflow-y: auto;
  max-height: 70vh;
}

.sidebar ul {
  list-style: none;
  padding: 0;
  margin: var(--space-3) 0 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.sidebar li button {
  width: 100%;
  background: none;
  border: none;
  text-align: left;
  padding: var(--space-2);
  border-radius: var(--border-radius-md);
  cursor: pointer;
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.sidebar li.active button,
.sidebar li button:hover {
  background-color: var(--color-primary-100);
}

.method {
  display: inline-block;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  text-transform: uppercase;
  color: #fff;
}

.method.GET {
  background-color: #22c55e;
}

.method.POST {
  background-color: #3b82f6;
}

.path {
  font-family: var(--font-family-mono);
  color: var(--color-text);
}

.details {
  background-color: var(--color-background);
  border-radius: var(--border-radius-xl);
  box-shadow: var(--shadow-md);
  padding: var(--space-5);
  min-height: 60vh;
}

.details-header {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.details-header code {
  background-color: var(--color-gray-100);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--border-radius-sm);
  font-family: var(--font-family-mono);
}

.param-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.param-grid label {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
}

.param-grid input,
.body-editor textarea {
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
  font-family: var(--font-family-mono);
}

.body-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.btn-primary {
  padding: var(--space-3) var(--space-6);
  background-color: var(--color-primary-600);
  color: #fff;
  border: none;
  border-radius: var(--border-radius-md);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: all var(--transition-base);
}

.btn-primary:disabled {
  background-color: var(--color-gray-400);
  cursor: not-allowed;
}

.response-section pre {
  background-color: #0f172a;
  color: #e2e8f0;
  padding: var(--space-4);
  border-radius: var(--border-radius-lg);
  overflow-x: auto;
}

@media (max-width: 960px) {
  .content {
    grid-template-columns: 1fr;
  }

  .sidebar {
    max-height: none;
  }
}
</style>
