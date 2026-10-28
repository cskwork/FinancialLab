import { describe, it, beforeEach, afterEach, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConfigStore } from './configStore'

describe('configStore', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('restores a stored live mode in regular builds', () => {
    vi.stubEnv('VITE_DEMO_ONLY', '')
    localStorage.setItem('finlab_backend_mode', 'live')

    const store = useConfigStore()
    store.restore()

    expect(store.isDemoOnly).toBe(false)
    expect(store.backendMode).toBe('live')
  })

  it('keeps mock mode in the demo-only build even when live was stored', () => {
    vi.stubEnv('VITE_DEMO_ONLY', 'true')
    localStorage.setItem('finlab_backend_mode', 'live')

    const store = useConfigStore()
    store.restore()

    expect(store.isDemoOnly).toBe(true)
    expect(store.isMockMode).toBe(true)
  })

  it('ignores switching to live in the demo-only build', () => {
    vi.stubEnv('VITE_DEMO_ONLY', 'true')

    const store = useConfigStore()
    store.setBackendMode('live')

    expect(store.backendMode).toBe('mock')
  })

  it('defaults live connections to the documented local backend and preserves explicit overrides', () => {
    const store = useConfigStore()
    expect(store.backendUrl).toBe('http://localhost:8080')
    localStorage.setItem('finlab_backend_url', 'https://example.test')
    store.restore()
    expect(store.backendUrl).toBe('https://example.test')
  })

  it('defaults to English locale when no stored locale exists', () => {
    const store = useConfigStore()
    store.restore()
    expect(store.locale).toBe('en')
  })

  it('restores stored Korean locale correctly', () => {
    localStorage.setItem('finlab_locale', 'ko')
    const store = useConfigStore()
    store.restore()
    expect(store.locale).toBe('ko')
  })
})
