/**
 * Equity curve
 * 백테스트 결과(체결 내역)와 종가로 날짜별 평가금액 곡선을 계산한다.
 * 워커 결과를 바꾸지 않고 화면에서만 사용하는 순수 함수.
 */

export interface PricePoint {
  date: string // YYYY-MM-DD
  close: number
}

export interface TradePoint {
  date: string
  shares: number
  cost: number
}

export interface EquityPoint {
  date: string
  dcaValue: number
  dcaInvested: number
  lumpValue: number
  lumpInvested: number
}

/**
 * 각 거래일 종가 기준으로 두 전략의 누적 보유 가치를 계산한다.
 * 거래는 해당 날짜 종가에 체결된 것으로 본다 (워커와 동일한 가정).
 */
export function buildEquityCurves(
  candles: PricePoint[],
  dcaTrades: TradePoint[],
  lumpTrades: TradePoint[]
): EquityPoint[] {
  const sortedCandles = [...candles].sort((a, b) => a.date.localeCompare(b.date))
  const dca = [...dcaTrades].sort((a, b) => a.date.localeCompare(b.date))
  const lump = [...lumpTrades].sort((a, b) => a.date.localeCompare(b.date))

  let dcaIdx = 0
  let lumpIdx = 0
  let dcaShares = 0
  let dcaInvested = 0
  let lumpShares = 0
  let lumpInvested = 0

  return sortedCandles.map((candle) => {
    while (dcaIdx < dca.length && dca[dcaIdx]!.date <= candle.date) {
      dcaShares += dca[dcaIdx]!.shares
      dcaInvested += dca[dcaIdx]!.cost
      dcaIdx += 1
    }
    while (lumpIdx < lump.length && lump[lumpIdx]!.date <= candle.date) {
      lumpShares += lump[lumpIdx]!.shares
      lumpInvested += lump[lumpIdx]!.cost
      lumpIdx += 1
    }

    return {
      date: candle.date,
      dcaValue: dcaShares * candle.close,
      dcaInvested,
      lumpValue: lumpShares * candle.close,
      lumpInvested
    }
  })
}
