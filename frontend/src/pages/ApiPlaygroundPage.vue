<template>
  <div class="page playground-page">
    <TitleBlock title="API Playground" sheet="App. A" sheet-label="Appendix">
      Inspect the OpenAPI contract and exercise mock or live endpoints directly from the browser.
      <template #actions>
        <span class="mode-hint">
          Current backend mode: <strong class="mode-chip" :class="{ live: configStore.backendMode === 'live' }">{{ configStore.backendMode.toUpperCase() }}</strong>
        </span>
      </template>
    </TitleBlock>

    <section class="content">
      <aside class="sidebar" aria-labelledby="endpoints-heading">
        <div class="block-head">
          <h2 id="endpoints-heading">Endpoints</h2>
          <span class="fig muted">{{ operations.length }}</span>
        </div>
        <p v-if="operations.length === 0" class="muted">Loading OpenAPI spec…</p>
        <ul class="endpoint-list">
          <li v-for="operation in operations" :key="operation.operationId">
            <button
              type="button"
              class="endpoint"
              :aria-current="selectedOperation?.operationId === operation.operationId ? 'true' : undefined"
              @click="selectOperation(operation)"
            >
              <span class="method" :class="operation.method.toLowerCase()">{{ operation.method }}</span>
              <span class="path fig">{{ operation.path }}</span>
            </button>
          </li>
        </ul>
      </aside>

      <div v-if="selectedOperation" class="details">
        <header class="details-header">
          <h2>{{ selectedOperation.summary }}</h2>
          <code>{{ selectedOperation.method.toUpperCase() }} {{ selectedOperation.path }}</code>
        </header>

        <section class="request-section" aria-labelledby="request-heading">
          <h3 id="request-heading" class="label">Request</h3>
          <div v-if="pathParamsKeys.length" class="param-grid">
            <div v-for="param in pathParamsKeys" :key="param" class="field">
              <label :for="`param-${param}`">{{ param }}</label>
              <input :id="`param-${param}`" v-model="pathParams[param]" type="text" />
            </div>
          </div>

          <div v-if="selectedOperation.requiresBody" class="field body-editor">
            <label for="request-body">Request Body (JSON)</label>
            <textarea id="request-body" v-model="requestPayload" rows="10" spellcheck="false"></textarea>
          </div>

          <button class="btn btn--primary" :disabled="isExecuting" @click="execute">
            <Send aria-hidden="true" />
            {{ isExecuting ? 'Executing...' : 'Execute' }}
          </button>
        </section>

        <section v-if="response" class="response-section" aria-labelledby="response-heading" aria-live="polite">
          <h3 id="response-heading" class="label">Response (<span class="fig">{{ response.status }}</span>)</h3>
          <pre>{{ responseBody }}</pre>
        </section>
      </div>

      <div v-else class="details details-empty">
        <EmptySheet compact>Select an endpoint to view details and execute it.</EmptySheet>
      </div>
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
import { Send } from 'lucide-vue-next'
import TitleBlock from '@/components/pad/TitleBlock.vue'
import EmptySheet from '@/components/pad/EmptySheet.vue'

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
.mode-hint {
  font-size: var(--text-sm);
  color: var(--ink-2);
}

.mode-chip {
  font-family: var(--font-label);
  font-weight: 700;
  letter-spacing: var(--tracking-label);
  padding: 1px 6px;
  border: 1px solid currentColor;
  color: var(--ink);
}

.mode-chip.live {
  background-color: var(--highlight);
  color: var(--highlight-ink);
  border-color: var(--highlight);
}

.content {
  display: grid;
  grid-template-columns: minmax(0, 22rem) minmax(0, 1fr);
  gap: var(--space-10);
  align-items: start;
}

.endpoint-list {
  list-style: none;
}

.endpoint {
  display: grid;
  grid-template-columns: 4.25rem minmax(0, 1fr);
  gap: var(--space-2);
  align-items: baseline;
  width: 100%;
  min-height: 44px;
  padding: var(--space-2) var(--space-2);
  text-align: left;
  border-bottom: 1px solid var(--rule);
}

.endpoint:hover {
  background-color: var(--paper-sunk);
}

.endpoint[aria-current='true'] {
  background-color: var(--ink);
  color: var(--paper);
}

.endpoint[aria-current='true'] .method {
  color: inherit;
}

.method {
  font-family: var(--font-label);
  font-weight: 700;
  font-size: var(--text-sm);
  letter-spacing: var(--tracking-label);
}

.method.get {
  color: var(--blue-pencil);
}

.method.delete {
  color: var(--red-pencil);
}

.path {
  font-size: var(--text-sm);
  overflow-wrap: anywhere;
}

.details {
  min-width: 0;
}

.details-header {
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-6);
  border-bottom: 2px solid var(--rule-strong);
}

.details-header h2 {
  margin-bottom: var(--space-2);
}

.details-header code {
  overflow-wrap: anywhere;
}

.request-section,
.response-section {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-4);
}

.response-section {
  margin-top: var(--space-10);
}

.response-section pre {
  width: 100%;
  max-height: 32rem;
}

.param-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
  gap: var(--space-4);
  width: 100%;
}

.body-editor {
  width: 100%;
}

.body-editor textarea {
  font-family: var(--font-figure);
  font-size: var(--text-sm);
}

@media (max-width: 900px) {
  .content {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
