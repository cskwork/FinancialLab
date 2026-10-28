import { describe, it, expect, afterEach } from 'vitest'
import i18n from '@/i18n'
import ko from '@/locales/ko.json'
import { failureMessage, localizeError, summarizeImportLocalized } from './localizedMessages'
import { summarizeImport, type RejectedImportRow } from './csvImport'
import { formatDateTime, formatSessionDay, formatSessionMonth, formatSignedUsd, formatUsd } from './localeFormat'

const HANGUL = /[가-힣]/

function useLocale(locale: 'ko' | 'en') {
  i18n.global.locale.value = locale
}

// 스토어·Mock API·FIFO 계산기·스키마가 사용자에게 돌려줄 수 있는 한국어 고정 문구
const errorSources = import.meta.glob(
  ['../stores/*.ts', '../services/api/*.ts', './fifoCalculator.ts', '!../**/*.spec.ts'],
  { query: '?raw', import: 'default', eager: true }
) as Record<string, string>

function koreanLiterals(): string[] {
  const found = new Set<string>()
  for (const source of Object.values(errorSources)) {
    const code = source
      .split('\n')
      .filter((line) => !/^\s*(\/\/|\*|\/\*)/.test(line))
      .join('\n')
    for (const match of code.matchAll(/'([^'\n]*[가-힣][^'\n]*)'/g)) found.add(match[1]!)
  }
  return [...found]
}

describe('localizeError', () => {
  afterEach(() => useLocale('ko'))

  it('has an English rendering for every fixed Korean error literal in stores and services', () => {
    const literals = koreanLiterals()
    expect(literals.length).toBeGreaterThan(30)
    expect(new Set(Object.values(ko.errors.known)).size).toBe(Object.keys(ko.errors.known).length)

    useLocale('en')
    const untranslated = literals.filter((literal) => HANGUL.test(localizeError(literal)))
    expect(untranslated).toEqual([])
  })

  it('keeps the original Korean text in Korean mode', () => {
    useLocale('ko')
    expect(localizeError('로그인이 필요합니다')).toBe('로그인이 필요합니다')
  })

  it('translates interpolated FIFO ledger errors, including the nested delete reason', () => {
    useLocale('en')
    expect(localizeError('AAPL 매도 수량이 보유 수량을 초과합니다')).toBe('AAPL sell quantity exceeds shares held')
    expect(localizeError('MSFT에 대한 보유 주식이 없습니다')).toBe('No shares of MSFT are held')
    expect(localizeError('이 거래에 의존하는 이후 매도가 있어 삭제할 수 없습니다 (AAPL 매도 수량이 보유 수량을 초과합니다)')).toBe(
      'This transaction cannot be deleted because a later sell depends on it (AAPL sell quantity exceeds shares held)'
    )
  })

  it('translates fixed English worker and CSV reasons into Korean', () => {
    useLocale('ko')
    for (const reason of [
      'No returns data',
      'Insufficient candle data',
      'Invalid timestamp',
      'Malformed CSV row (unbalanced quotes)',
      'SELL requires symbol, qty, and price'
    ]) {
      expect(localizeError(reason)).toMatch(HANGUL)
    }
  })

  it('passes unknown server text through untouched', () => {
    useLocale('en')
    expect(localizeError('upstream 502: bad gateway')).toBe('upstream 502: bad gateway')
    expect(localizeError(null)).toBe('')
  })

  it('localizes each field validation reason without discarding additional errors', () => {
    useLocale('en')
    expect(localizeError('endDate must be on or after startDate\nmust not be blank')).toBe(
      'End date must be on or after the start date\nThis field is required'
    )
    useLocale('ko')
    expect(localizeError('endDate must be on or after startDate')).toBe('종료일은 시작일과 같거나 이후여야 합니다')
  })
})

describe('failureMessage', () => {
  afterEach(() => useLocale('ko'))

  it('always names the failed action and appends a localized reason when there is one', () => {
    useLocale('en')
    expect(failureMessage('goals.goalSaveFailed', null)).toBe('Could not save the goal.')
    expect(failureMessage('goals.goalSaveFailed', '로그인이 필요합니다')).toBe(
      'Could not save the goal. Reason: You need to log in'
    )
    useLocale('ko')
    expect(failureMessage('transactions.deleteFailed', 'Invalid timestamp')).toBe(
      '거래를 삭제하지 못했습니다. 사유: timestamp가 올바르지 않습니다'
    )
  })
})

describe('summarizeImportLocalized', () => {
  afterEach(() => useLocale('ko'))

  const rejected: RejectedImportRow[] = [
    {
      request: { portfolioId: 1, type: 'SELL', symbol: 'MSFT', qty: 1, price: 1, fee: 0, timestamp: '2024-01-05T00:00:00.000Z' },
      reason: 'MSFT에 대한 보유 주식이 없습니다'
    }
  ]
  const invalid = [{ row: 3, reason: 'Invalid timestamp' }]

  it('reaches the same completeness verdict as the contract summary', () => {
    for (const [created, inv, rej] of [
      [3, [], []],
      [2, invalid, rejected],
      [0, invalid, []]
    ] as const) {
      expect(summarizeImportLocalized(created, [...inv], [...rej]).complete).toBe(
        summarizeImport(created, [...inv], [...rej]).complete
      )
    }
  })

  it('reports counts, the first invalid row and the first rejection in English', () => {
    useLocale('en')
    expect(summarizeImportLocalized(1, [], []).message).toBe('1 transaction imported.')
    const { message } = summarizeImportLocalized(2, invalid, rejected)
    expect(message).toContain('2 transactions imported')
    expect(message).toContain('1 invalid row skipped')
    expect(message).toContain('1 rejected')
    expect(message).toContain('row 3: Invalid timestamp')
    expect(message).toContain('SELL MSFT 2024-01-05: No shares of MSFT are held')
    expect(message).not.toMatch(HANGUL)
  })

  it('reports the same facts in Korean', () => {
    useLocale('ko')
    const { message } = summarizeImportLocalized(2, invalid, rejected)
    expect(message).toContain('거래 2건 가져옴')
    expect(message).toContain('유효하지 않은 행 1개 건너뜀')
    expect(message).toContain('3행: timestamp가 올바르지 않습니다')
    expect(message).toContain('매도 MSFT 2024-01-05: MSFT에 대한 보유 주식이 없습니다')
  })
})

describe('locale formatting', () => {
  it('keeps the dollar sign and sign marker while grouping digits per locale', () => {
    expect(formatUsd(1234.5, 'en')).toBe('$1,234.50')
    expect(formatUsd(1234.5, 'ko')).toBe('$1,234.50')
    expect(formatSignedUsd(-12.3, 'ko')).toBe('-$12.30')
    expect(formatSignedUsd(0, 'en')).toBe('+$0.00')
  })

  it('renders dates in the selected language without shifting session days', () => {
    expect(formatSessionDay('2024-01-02', 'en')).toBe('Jan 2, 2024')
    expect(formatSessionDay('2024-01-02', 'ko')).toMatch(/2024.*1.*2/)
    expect(formatSessionDay('2024-01-02', 'ko')).toMatch(HANGUL)
    expect(formatSessionMonth('2024-03-01', 'ko')).toBe('3월')
    expect(formatSessionMonth('2024-03-01', 'en')).toBe('Mar')
    expect(formatDateTime('2024-01-02T14:30:00.000Z', 'en')).not.toMatch(HANGUL)
    expect(formatDateTime('2024-01-02T14:30:00.000Z', 'ko')).toMatch(HANGUL)
  })
})
