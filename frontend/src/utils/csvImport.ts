/**
 * CSV Import 저장 헬퍼
 * CSV 행을 검증해 거부 사유를 모으고, 통과한 행을 FIFO 검증을 통과하도록 시간순으로 저장한다.
 */

import type { CreateTransactionRequest } from '@/services/api/schemas'

export interface RejectedImportRow {
  request: CreateTransactionRequest
  reason: string
}

export interface ImportResult {
  created: number
  rejected: RejectedImportRow[]
}

/**
 * 워커 파싱/검증 단계에서 거부된 행. reason은 고정 문구만 담고 셀 원문은 넣지 않는다.
 */
export interface InvalidCsvRow {
  row: number
  reason: string
}

export type ParsedCsvRow = Record<string, unknown>

/**
 * CSV 원본 행(헤더 소문자화 이후)
 */
export interface CsvTransactionRow {
  type?: string
  symbol?: string
  qty?: string
  price?: string
  fee?: string
  timestamp?: string
  note?: string
}

const VALID_TYPES = ['BUY', 'SELL', 'DEPOSIT', 'WITHDRAW']

function parseOptionalNumber(value: string | undefined): number | undefined | null {
  if (!value) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export type CsvRowParseResult = { ok: true; value: ParsedCsvRow } | { ok: false; reason: string }

/**
 * CSV 원본 행을 검증·변환한다. 실패하면 사용자에게 보여줄 수 있는 사유를 반환한다.
 */
export function parseCsvRow(row: CsvTransactionRow): CsvRowParseResult {
  // 필수 필드 검증
  if (!row.type || !row.timestamp) {
    return { ok: false, reason: 'Missing required fields (type, timestamp)' }
  }

  // 타입 검증
  const type = row.type.toUpperCase()
  if (!VALID_TYPES.includes(type)) {
    return { ok: false, reason: 'Invalid type (expected BUY, SELL, DEPOSIT or WITHDRAW)' }
  }

  // BUY/SELL은 symbol, qty, price 필수
  if ((type === 'BUY' || type === 'SELL') && (!row.symbol || !row.qty || !row.price)) {
    return { ok: false, reason: `${type} requires symbol, qty, and price` }
  }

  const qty = parseOptionalNumber(row.qty)
  const price = parseOptionalNumber(row.price)
  const fee = parseOptionalNumber(row.fee)
  if (qty === null || price === null || fee === null) {
    return { ok: false, reason: 'Invalid number in qty, price, or fee' }
  }

  const time = Date.parse(row.timestamp)
  if (Number.isNaN(time)) {
    return { ok: false, reason: 'Invalid timestamp' }
  }

  return {
    ok: true,
    value: {
      type,
      symbol: row.symbol || undefined,
      qty,
      price,
      fee: fee ?? 0,
      timestamp: new Date(time).toISOString(),
      note: row.note || undefined
    }
  }
}

/**
 * 모든 행을 검증해 통과한 행과 거부된 행(1부터 시작하는 데이터 행 번호)을 함께 반환한다.
 * malformed: CSV 파서가 구조 오류를 보고한 행의 0부터 시작하는 인덱스
 */
export function parseCsvRows(
  rows: CsvTransactionRow[],
  malformed: ReadonlySet<number> = new Set()
): {
  valid: ParsedCsvRow[]
  invalid: InvalidCsvRow[]
} {
  const valid: ParsedCsvRow[] = []
  const invalid: InvalidCsvRow[] = []

  rows.forEach((row, index) => {
    let parsed: CsvRowParseResult
    try {
      parsed = malformed.has(index)
        ? { ok: false, reason: 'Malformed CSV row (unbalanced quotes)' }
        : parseCsvRow(row)
    } catch {
      parsed = { ok: false, reason: 'Could not parse row' }
    }
    if (parsed.ok) {
      valid.push(parsed.value)
    } else {
      invalid.push({ row: index + 1, reason: parsed.reason })
    }
  })

  return { valid, invalid }
}

/**
 * 저장 함수: 성공하면 null, 거부되면 사유를 반환한다.
 */
export type ImportPersist = (request: CreateTransactionRequest) => Promise<string | null>

/**
 * 워커 파싱 결과 행을 거래 생성 요청으로 변환
 */
export function toImportRequests(
  rows: Array<Record<string, unknown>>,
  portfolioId: number
): CreateTransactionRequest[] {
  return rows.map((row) => ({
    portfolioId,
    type: (row.type as CreateTransactionRequest['type']) ?? 'BUY',
    symbol: (row.symbol as string | undefined) || undefined,
    qty: typeof row.qty === 'number' ? row.qty : undefined,
    price: typeof row.price === 'number' ? row.price : undefined,
    fee: typeof row.fee === 'number' ? row.fee : 0,
    timestamp:
      typeof row.timestamp === 'string' ? new Date(row.timestamp).toISOString() : new Date().toISOString(),
    note: (row.note as string | undefined) || undefined
  }))
}

/**
 * 시간순(오래된 것 먼저) 정렬. 최신순 CSV도 SELL이 앞선 BUY 뒤에 저장되도록 한다.
 * 같은 시각의 행은 파일 순서를 유지한다 (Array.prototype.sort는 안정 정렬).
 */
export function orderForImport(requests: CreateTransactionRequest[]): CreateTransactionRequest[] {
  return [...requests].sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp))
}

/**
 * 시간순으로 저장하고 성공/거부 건수를 모두 반환한다. 거부된 행은 숨기지 않는다.
 */
export async function importTransactions(
  requests: CreateTransactionRequest[],
  persist: ImportPersist
): Promise<ImportResult> {
  const result: ImportResult = { created: 0, rejected: [] }

  for (const request of orderForImport(requests)) {
    const reason = await persist(request)
    if (reason === null) {
      result.created += 1
    } else {
      result.rejected.push({ request, reason })
    }
  }

  return result
}

/**
 * 거부된 행을 사용자에게 보여줄 한 줄 요약
 */
export function describeRejectedRow({ request, reason }: RejectedImportRow): string {
  const symbol = request.symbol ? ` ${request.symbol}` : ''
  return `${request.type}${symbol} ${request.timestamp.slice(0, 10)}: ${reason}`
}

/**
 * 파싱 거부와 저장 거부를 합쳐 사용자 알림을 만든다. 거부가 하나라도 있으면 성공으로 보지 않는다.
 */
export function summarizeImport(
  created: number,
  invalid: InvalidCsvRow[],
  rejected: RejectedImportRow[]
): { complete: boolean; message: string } {
  const total = invalid.length + rejected.length
  if (total === 0) {
    return { complete: true, message: `${created} transactions imported.` }
  }

  const parts = [`${created} transactions imported`]
  if (invalid.length > 0) parts.push(`${invalid.length} invalid rows skipped`)
  if (rejected.length > 0) parts.push(`${rejected.length} rejected`)
  const details: string[] = []
  if (invalid[0]) details.push(`First invalid: row ${invalid[0].row}: ${invalid[0].reason}`)
  if (rejected[0]) details.push(`First rejected: ${describeRejectedRow(rejected[0])}`)

  return { complete: false, message: `${parts.join(', ')}. ${details.join('. ')}` }
}
