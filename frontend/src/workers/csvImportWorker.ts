/**
 * CSV Import Worker
 * PapaParse를 사용하여 CSV 파일을 파싱하는 Web Worker
 */

import Papa from 'papaparse'
import { parseCsvRows, type CsvTransactionRow, type InvalidCsvRow } from '@/utils/csvImport'

// Worker 메시지 타입
interface WorkerRequest {
  type: 'parse'
  file: File
}

interface WorkerResponse {
  type: 'success' | 'error' | 'progress'
  data?: unknown
  invalid?: InvalidCsvRow[]
  error?: string
  progress?: number
}

// Worker 메시지 핸들러
self.addEventListener('message', (event: MessageEvent<WorkerRequest>) => {
  const { type, file } = event.data

  if (type === 'parse') {
    parseCSV(file)
  }
})

/**
 * CSV 파싱
 */
function parseCSV(file: File) {
  // step을 쓰면 complete의 results.data가 비어 있으므로 행을 직접 모은다
  const rows: CsvTransactionRow[] = []
  const malformed = new Set<number>()

  Papa.parse<CsvTransactionRow>(file, {
    header: true,
    skipEmptyLines: true,
    transformHeader: (header) => header.trim().toLowerCase(),
    step: (results) => {
      // 따옴표가 깨진 행은 필드 경계를 믿을 수 없으므로 파싱 거부로 돌린다
      if (results.errors.some((error) => error.type === 'Quotes')) {
        malformed.add(rows.length)
      }
      rows.push(results.data)

      // 진행률 업데이트 (파일 크기 기준)
      const progress = Math.round(
        (results.meta.cursor / file.size) * 100
      )

      postMessage({
        type: 'progress',
        progress
      } as WorkerResponse)
    },
    complete: () => {
      // 데이터 검증 및 변환. 거부된 행은 버리지 않고 사유와 함께 돌려준다
      const { valid, invalid } = parseCsvRows(rows, malformed)

      postMessage({
        type: 'success',
        data: valid,
        invalid
      } as WorkerResponse)
    },
    error: (error) => {
      postMessage({
        type: 'error',
        error: error.message
      } as WorkerResponse)
    }
  })
}

export {}
