import crypto, { webcrypto } from 'node:crypto'
import type { BinaryToTextEncoding } from 'node:crypto'
import { Buffer } from 'node:buffer'

if (typeof globalThis.crypto?.getRandomValues !== 'function') {
  // Vitest가 Node 16 환경에서 Web Crypto API를 사용할 수 있도록 polyfill을 추가합니다.
  globalThis.crypto = webcrypto as unknown as Crypto
}

if (typeof crypto.getRandomValues !== 'function' && typeof webcrypto.getRandomValues === 'function') {
  // 테스트 환경에서 node:crypto 모듈을 사용하는 코드가 안전하게 동작하도록 Polyfill을 복제합니다.
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
  // Vitest 환경에서 crypto.hash API가 요구되므로 Node 16에서도 동작하도록 Polyfill을 제공합니다.
  Reflect.set(crypto, 'hash', (algorithm: string, data: HashInput, outputEncoding?: HashEncoding) => {
    const hash = crypto.createHash(algorithm)
    hash.update(toBuffer(data))
    if (!outputEncoding || outputEncoding === 'buffer') {
      return hash.digest()
    }

    return hash.digest(outputEncoding)
  })
}
