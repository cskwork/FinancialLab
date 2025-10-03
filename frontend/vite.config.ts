import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import crypto, { webcrypto } from 'node:crypto'
import type { BinaryToTextEncoding } from 'node:crypto'
import { Buffer } from 'node:buffer'

const globalWithCrypto = globalThis as typeof globalThis & { crypto?: typeof webcrypto }

if (typeof globalWithCrypto.crypto?.getRandomValues !== 'function') {
  // Node 16 환경에서 Vite 설정이 Web Crypto API를 필요로 하므로 최소 polyfill을 제공합니다.
  globalWithCrypto.crypto = webcrypto
}

if (typeof crypto.getRandomValues !== 'function' && typeof webcrypto.getRandomValues === 'function') {
  // Vite 내부가 node:crypto 모듈의 getRandomValues를 직접 호출하므로 동일한 Polyfill을 덧입힙니다.
  Reflect.set(crypto, 'getRandomValues', webcrypto.getRandomValues.bind(webcrypto))
}

type HashInput = string | ArrayBuffer | NodeJS.ArrayBufferView
type HashEncoding = BinaryToTextEncoding | 'buffer' | undefined

const toBuffer = (data: HashInput): Buffer => {
  if (typeof data === 'string') {
    return Buffer.from(data)
  }

  if (data instanceof ArrayBuffer) {
    return Buffer.from(data)
  }

  const view = data as NodeJS.ArrayBufferView
  return Buffer.from(view.buffer, view.byteOffset, view.byteLength)
}

if (typeof crypto.hash !== 'function') {
  // Node 16에는 crypto.hash가 없으므로 createHash 기반 Polyfill을 제공합니다.
  Reflect.set(crypto, 'hash', (algorithm: string, data: HashInput, outputEncoding?: HashEncoding) => {
    const hash = crypto.createHash(algorithm)
    hash.update(toBuffer(data))
    if (!outputEncoding || outputEncoding === 'buffer') {
      return hash.digest()
    }

    return hash.digest(outputEncoding)
  })
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: ['src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: ['node_modules/', 'src/**/*.spec.ts']
    }
  }
})
