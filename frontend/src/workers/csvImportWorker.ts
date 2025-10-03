/**
 * CSV Import Worker
 * PapaParse를 사용하여 CSV 파일을 파싱하는 Web Worker
 */

import Papa from 'papaparse'

// Worker 메시지 타입
interface WorkerRequest {
  type: 'parse'
  file: File
}

interface WorkerResponse {
  type: 'success' | 'error' | 'progress'
  data?: unknown
  error?: string
  progress?: number
}

// CSV 데이터 행 타입
interface TransactionRow {
  type: string
  symbol?: string
  qty?: string
  price?: string
  fee?: string
  timestamp: string
  note?: string
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
  Papa.parse<TransactionRow>(file, {
    header: true,
    skipEmptyLines: true,
    transformHeader: (header) => header.trim().toLowerCase(),
    step: (results) => {
      // 진행률 업데이트 (파일 크기 기준)
      const progress = Math.round(
        (results.meta.cursor / file.size) * 100
      )

      postMessage({
        type: 'progress',
        progress
      } as WorkerResponse)
    },
    complete: (results) => {
      // 데이터 검증 및 변환
      const transactions = results.data
        .map((row, index) => {
          try {
            // 필수 필드 검증
            if (!row.type || !row.timestamp) {
              console.warn(`Row ${index + 1}: Missing required fields`)
              return null
            }

            // 타입 검증
            const type = row.type.toUpperCase()
            if (!['BUY', 'SELL', 'DEPOSIT', 'WITHDRAW'].includes(type)) {
              console.warn(`Row ${index + 1}: Invalid type "${row.type}"`)
              return null
            }

            // BUY/SELL은 symbol, qty, price 필수
            if ((type === 'BUY' || type === 'SELL') && (!row.symbol || !row.qty || !row.price)) {
              console.warn(`Row ${index + 1}: ${type} requires symbol, qty, and price`)
              return null
            }

            return {
              type,
              symbol: row.symbol || undefined,
              qty: row.qty ? parseFloat(row.qty) : undefined,
              price: row.price ? parseFloat(row.price) : undefined,
              fee: row.fee ? parseFloat(row.fee) : 0,
              timestamp: new Date(row.timestamp).toISOString(),
              note: row.note || undefined
            }
          } catch (error) {
            console.error(`Row ${index + 1}: Parse error`, error)
            return null
          }
        })
        .filter((tx) => tx !== null)

      postMessage({
        type: 'success',
        data: transactions
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
