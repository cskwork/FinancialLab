/**
 * 화면 표시용 메시지 현지화
 * 스토어·Mock API·워커·CSV 검증이 만드는 고정 문구는 계약(테스트 포함)을 그대로 두고,
 * 화면에 그릴 때만 현재 언어의 문구로 바꾼다. 모르는 문구(실제 응답 본문 등)는 원문 그대로 보여준다.
 */

import i18n from '@/i18n'
import ko from '@/locales/ko.json'
import type { InvalidCsvRow, RejectedImportRow } from './csvImport'

const t = i18n.global.t

// 스토어·Mock API의 한국어 고정 문구 → errors.known.* 키 (ko.json 값이 원문과 같아야 한다)
const KOREAN_SOURCE = new Map(Object.entries(ko.errors.known).map(([key, value]) => [value, `errors.known.${key}`]))

// 워커와 CSV 검증의 영어 고정 문구
const ENGLISH_SOURCE: Record<string, string> = {
  'endDate must be on or after startDate': 'errors.validation.planDateOrder',
  'must not be blank': 'errors.validation.required',
  'No returns data': 'errors.worker.noReturnsData',
  'No candles available': 'errors.worker.noCandles',
  'Insufficient candle data': 'errors.worker.insufficientCandles',
  'Unknown error': 'errors.worker.unknown',
  'Missing required fields (type, timestamp)': 'transactions.csv.missingFields',
  'Invalid type (expected BUY, SELL, DEPOSIT or WITHDRAW)': 'transactions.csv.invalidType',
  'Invalid number in qty, price, or fee': 'transactions.csv.invalidNumber',
  'Invalid timestamp': 'transactions.csv.invalidTimestamp',
  'Malformed CSV row (unbalanced quotes)': 'transactions.csv.malformedRow',
  'Could not parse row': 'transactions.csv.unparsableRow'
}

// 값이 끼워진 고정 문구 (FIFO 원장 검증, CSV 행 검증)
const PATTERNS: Array<[RegExp, (match: RegExpMatchArray) => string]> = [
  [/^(\S+) 매도 수량이 보유 수량을 초과합니다$/, (m) => t('errors.ledger.sellExceeds', { symbol: m[1] })],
  [/^(\S+)에 대한 보유 주식이 없습니다$/, (m) => t('errors.ledger.noShares', { symbol: m[1] })],
  [
    /^이 거래에 의존하는 이후 매도가 있어 삭제할 수 없습니다 \((.*)\)$/,
    (m) => t('errors.ledger.deleteBlocked', { detail: localizeError(m[1]) })
  ],
  [/^(BUY|SELL) requires symbol, qty, and price$/, (m) => t('transactions.csv.tradeFieldsRequired', { type: m[1] })]
]

/** 알려진 앱 오류 문구는 현재 언어로, 그 밖의 원문(서버 응답 등)은 그대로 반환한다. */
export function localizeError(message: string | null | undefined): string {
  if (!message) return ''
  if (message.includes('\n')) return message.split('\n').map((reason) => localizeError(reason)).join('\n')
  const key = KOREAN_SOURCE.get(message) ?? ENGLISH_SOURCE[message]
  if (key) return t(key)
  for (const [pattern, render] of PATTERNS) {
    const match = message.match(pattern)
    if (match) return render(match)
  }
  return message
}

/** 실패한 작업의 현지화된 제목과 (있다면) 사유를 한 문장으로 묶는다. 사유가 없어도 실패는 알린다. */
export function failureMessage(actionKey: string, detail?: string | null): string {
  const action = t(actionKey)
  const reason = localizeError(detail)
  return reason ? t('errors.withDetail', { action, detail: reason }) : action
}

const TYPE_KEYS: Record<string, string> = {
  BUY: 'transactions.buy',
  SELL: 'transactions.sell',
  DEPOSIT: 'transactions.deposit',
  WITHDRAW: 'transactions.withdraw'
}

/** 거래 유형 표시 이름. 저장·필터·CSV 값(BUY 등)은 그대로 둔다. */
export function transactionTypeLabel(type: string): string {
  const key = TYPE_KEYS[type]
  return key ? t(key) : type
}

/**
 * CSV 가져오기 결과 요약 (summarizeImport와 같은 판정, 현재 언어 문구).
 * 거부가 하나라도 있으면 complete=false.
 */
export function summarizeImportLocalized(
  created: number,
  invalid: InvalidCsvRow[],
  rejected: RejectedImportRow[]
): { complete: boolean; message: string } {
  if (invalid.length + rejected.length === 0) {
    return { complete: true, message: t('transactions.importComplete', { count: created }, created) }
  }

  const parts = [t('transactions.importImported', { count: created }, created)]
  if (invalid.length > 0) parts.push(t('transactions.importInvalid', { count: invalid.length }, invalid.length))
  if (rejected.length > 0) parts.push(t('transactions.importRejected', { count: rejected.length }, rejected.length))

  const details: string[] = []
  const firstInvalid = invalid[0]
  if (firstInvalid) {
    details.push(t('transactions.importFirstInvalid', { row: firstInvalid.row, reason: localizeError(firstInvalid.reason) }))
  }
  const firstRejected = rejected[0]
  if (firstRejected) {
    const { request, reason } = firstRejected
    const what = [transactionTypeLabel(request.type), request.symbol, request.timestamp.slice(0, 10)]
      .filter(Boolean)
      .join(' ')
    details.push(t('transactions.importFirstRejected', { detail: `${what}: ${localizeError(reason)}` }))
  }

  return { complete: false, message: `${parts.join(', ')}. ${details.join(' ')}` }
}
