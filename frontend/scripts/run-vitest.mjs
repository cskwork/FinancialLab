#!/usr/bin/env node
import crypto, { webcrypto } from 'node:crypto'
import { Buffer } from 'node:buffer'
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'
import { pathToFileURL } from 'node:url'

if (typeof globalThis.crypto?.getRandomValues !== 'function') {
  // Node 16 런타임에서 Vitest CLI가 Web Crypto API를 필요로 하므로 Polyfill을 주입합니다.
  globalThis.crypto = webcrypto
}

if (typeof crypto.getRandomValues !== 'function' && typeof webcrypto.getRandomValues === 'function') {
  // Vite가 node:crypto 모듈의 getRandomValues를 호출하므로 동일한 Polyfill을 제공합니다.
  Reflect.set(crypto, 'getRandomValues', webcrypto.getRandomValues.bind(webcrypto))
}

const toBuffer = (data) => {
  if (typeof data === 'string') {
    return Buffer.from(data)
  }

  if (data instanceof ArrayBuffer) {
    return Buffer.from(data)
  }

  if (ArrayBuffer.isView(data)) {
    return Buffer.from(data.buffer, data.byteOffset, data.byteLength)
  }

  throw new TypeError('Unsupported data type for crypto.hash polyfill')
}

if (typeof crypto.hash !== 'function') {
  // Node 16에는 crypto.hash가 없으므로 createHash 기반 Polyfill을 제공합니다.
  Reflect.set(crypto, 'hash', (algorithm, data, outputEncoding) => {
    const hash = crypto.createHash(algorithm)
    hash.update(toBuffer(data))
    if (!outputEncoding || outputEncoding === 'buffer') {
      return hash.digest()
    }

    return hash.digest(outputEncoding)
  })
}

if (process.argv[1] && !process.argv[1].includes('vitest')) {
  process.argv.splice(1, 1, 'vitest')
}

// Vitest 4 no longer exports its CLI file, so load it through the package's own bin entry.
const require = createRequire(import.meta.url)
const vitestDir = dirname(require.resolve('vitest/package.json'))
await import(pathToFileURL(join(vitestDir, require('vitest/package.json').bin.vitest)).href)
