import { describe, it, expect } from 'vitest'
import ko from './ko.json'
import en from './en.json'

type Messages = { [key: string]: string | Messages }

function flatten(messages: Messages, prefix = ''): Map<string, string> {
  const out = new Map<string, string>()
  for (const [key, value] of Object.entries(messages)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'string') out.set(path, value)
    else flatten(value, path).forEach((v, k) => out.set(k, v))
  }
  return out
}

// 이름 있는 자리표시자 집합만 비교한다 ({'demo@finlab.app'} 같은 리터럴 보간 제외, 복수형 분기마다 반복되는 이름은 한 번)
function placeholders(message: string): string[] {
  return [...new Set([...message.matchAll(/\{\s*([A-Za-z_]\w*)\s*\}/g)].map((m) => m[1]!))].sort()
}

const koFlat = flatten(ko as Messages)
const enFlat = flatten(en as Messages)

// 앱 소스 원문 (테스트 파일 제외)
const sources = import.meta.glob(['../**/*.ts', '../**/*.vue', '!../**/*.spec.ts'], {
  query: '?raw',
  import: 'default',
  eager: true
}) as Record<string, string>

describe('locale files', () => {
  it('describes FIFO average cost as including buy fees in every locale', () => {
    expect(en.dashboard.tableHint).toContain('includes buy fees')
    expect(en.terms.avgCost).toContain('including buy fees')
    expect(ko.dashboard.tableHint).toContain('매수 수수료를 포함')
    expect(ko.terms.avgCost).toContain('매수 수수료 포함')
  })
  it('have the same keys in both directions', () => {
    const missingInEn = [...koFlat.keys()].filter((key) => !enFlat.has(key))
    const missingInKo = [...enFlat.keys()].filter((key) => !koFlat.has(key))
    expect(missingInEn).toEqual([])
    expect(missingInKo).toEqual([])
  })

  it('use the same interpolation placeholders for every key', () => {
    const mismatched = [...koFlat.keys()]
      .filter((key) => enFlat.has(key))
      .filter((key) => placeholders(koFlat.get(key)!).join() !== placeholders(enFlat.get(key)!).join())
    expect(mismatched).toEqual([])
  })

  it('have no empty messages', () => {
    const empty = [...koFlat, ...enFlat].filter(([, value]) => value.trim() === '').map(([key]) => key)
    expect(empty).toEqual([])
  })

  it('define every static message key the app references', () => {
    const patterns = [
      /\bt\(\s*'([\w.]+)'/g,
      /keypath="([\w.]+)"/g,
      /titleKey: '([\w.]+)'/g,
      /failureMessage\(\s*'([\w.]+)'/g,
      /\{ key: '([\w.]+\.[\w.]+)'/g
    ]
    const used = new Set<string>()
    for (const source of Object.values(sources)) {
      for (const pattern of patterns) {
        for (const match of source.matchAll(pattern)) used.add(match[1]!)
      }
    }

    expect(used.size).toBeGreaterThan(200)
    const undefinedKeys = [...used].filter((key) => !koFlat.has(key) || !enFlat.has(key))
    expect(undefinedKeys).toEqual([])
  })
})
