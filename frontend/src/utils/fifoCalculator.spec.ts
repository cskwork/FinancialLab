/**
 * FIFO Calculator Unit Tests
 */

import { describe, it, expect, beforeEach } from 'vitest'
import { FifoCalculator, calculateHoldingsFromTransactions } from './fifoCalculator'
import type { Transaction } from '@/services/db/schema'

describe('FifoCalculator', () => {
  let calculator: FifoCalculator

  beforeEach(() => {
    calculator = new FifoCalculator()
  })

  describe('매수 거래 처리', () => {
    it('단일 매수 거래를 올바르게 처리해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        }
      ]

      const holdings = calculator.processBuy(transactions)

      expect(holdings.size).toBe(1)
      const appleHolding = holdings.get('AAPL')
      expect(appleHolding).toBeDefined()
      expect(appleHolding!.totalShares).toBe(100)
      expect(appleHolding!.avgCost).toBe(150.0)
      expect(appleHolding!.lots).toHaveLength(1)
    })

    it('여러 매수 거래의 평균 단가를 올바르게 계산해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        },
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 50,
          price: 160.0,
          fee: 0.5,
          timestamp: new Date('2024-01-02')
        }
      ]

      const holdings = calculator.processBuy(transactions)

      const appleHolding = holdings.get('AAPL')
      expect(appleHolding!.totalShares).toBe(150)

      // (100*150 + 50*160) / 150 = 153.33...
      expect(appleHolding!.avgCost).toBeCloseTo(153.333, 2)
      expect(appleHolding!.lots).toHaveLength(2)
    })

    it('여러 종목의 매수를 독립적으로 처리해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        },
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'MSFT',
          qty: 50,
          price: 300.0,
          fee: 1.5,
          timestamp: new Date('2024-01-02')
        }
      ]

      const holdings = calculator.processBuy(transactions)

      expect(holdings.size).toBe(2)
      expect(holdings.get('AAPL')!.totalShares).toBe(100)
      expect(holdings.get('MSFT')!.totalShares).toBe(50)
    })
  })

  describe('매도 거래 처리 (FIFO)', () => {
    it('전체 로트 매도를 올바르게 처리해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        },
        {
          portfolioId: 1,
          type: 'SELL',
          symbol: 'AAPL',
          qty: 100,
          price: 160.0,
          fee: 1.0,
          timestamp: new Date('2024-01-05')
        }
      ]

      const holdings = calculator.processBuy(transactions)

      expect(holdings.size).toBe(0) // 모두 매도
    })

    it('일부 로트 매도를 올바르게 처리해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        },
        {
          portfolioId: 1,
          type: 'SELL',
          symbol: 'AAPL',
          qty: 30,
          price: 160.0,
          fee: 0.5,
          timestamp: new Date('2024-01-05')
        }
      ]

      const holdings = calculator.processBuy(transactions)

      const appleHolding = holdings.get('AAPL')
      expect(appleHolding!.totalShares).toBe(70)
      expect(appleHolding!.avgCost).toBe(150.0) // 같은 로트에서 매도했으므로 평균단가 유지
      expect(appleHolding!.lots).toHaveLength(1)
    })

    it('FIFO 순서로 여러 로트를 매도해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        },
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 50,
          price: 160.0,
          fee: 0.5,
          timestamp: new Date('2024-01-02')
        },
        {
          portfolioId: 1,
          type: 'SELL',
          symbol: 'AAPL',
          qty: 120, // 첫 로트 100 + 두번째 로트 20
          price: 170.0,
          fee: 1.0,
          timestamp: new Date('2024-01-05')
        }
      ]

      const holdings = calculator.processBuy(transactions)

      const appleHolding = holdings.get('AAPL')
      expect(appleHolding!.totalShares).toBe(30) // 남은 수량
      expect(appleHolding!.avgCost).toBe(160.0) // 두 번째 로트만 남음
      expect(appleHolding!.lots).toHaveLength(1)
    })

    it('보유량보다 많은 수량 매도 시 에러를 발생시켜야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        },
        {
          portfolioId: 1,
          type: 'SELL',
          symbol: 'AAPL',
          qty: 150, // 보유량 초과
          price: 160.0,
          fee: 1.0,
          timestamp: new Date('2024-01-05')
        }
      ]

      expect(() => calculator.processBuy(transactions)).toThrow('보유 수량을 초과합니다')
    })

    it('보유하지 않은 종목 매도 시 에러를 발생시켜야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'SELL',
          symbol: 'AAPL',
          qty: 50,
          price: 160.0,
          fee: 1.0,
          timestamp: new Date('2024-01-05')
        }
      ]

      expect(() => calculator.processBuy(transactions)).toThrow('보유 주식이 없습니다')
    })
  })

  describe('미실현 손익 계산', () => {
    it('가격 상승 시 양의 미실현 손익을 계산해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        }
      ]

      calculator.processBuy(transactions)
      const unrealizedPnL = calculator.calculateUnrealizedPnL('AAPL', 160.0)

      // (160 - 150) * 100 = 1000
      expect(unrealizedPnL).toBe(1000)
    })

    it('가격 하락 시 음의 미실현 손익을 계산해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        }
      ]

      calculator.processBuy(transactions)
      const unrealizedPnL = calculator.calculateUnrealizedPnL('AAPL', 140.0)

      // (140 - 150) * 100 = -1000
      expect(unrealizedPnL).toBe(-1000)
    })

    it('여러 로트의 평균단가 기준으로 계산해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        },
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 160.0,
          fee: 1.0,
          timestamp: new Date('2024-01-02')
        }
      ]

      calculator.processBuy(transactions)
      const unrealizedPnL = calculator.calculateUnrealizedPnL('AAPL', 170.0)

      // avgCost = (150*100 + 160*100) / 200 = 155
      // unrealized = (170 - 155) * 200 = 3000
      expect(unrealizedPnL).toBe(3000)
    })
  })

  describe('복합 시나리오', () => {
    it('여러 매수/매도가 섞인 거래를 올바르게 처리해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        },
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 50,
          price: 160.0,
          fee: 0.5,
          timestamp: new Date('2024-01-02')
        },
        {
          portfolioId: 1,
          type: 'SELL',
          symbol: 'AAPL',
          qty: 80,
          price: 170.0,
          fee: 1.0,
          timestamp: new Date('2024-01-03')
        },
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 30,
          price: 165.0,
          fee: 0.5,
          timestamp: new Date('2024-01-04')
        }
      ]

      const holdings = calculator.processBuy(transactions)

      const appleHolding = holdings.get('AAPL')
      // 100 + 50 - 80 + 30 = 100
      expect(appleHolding!.totalShares).toBe(100)

      // FIFO: 첫 로트 100 중 80 매도, 20 남음
      // 남은 로트: 20@150, 50@160, 30@165
      // avgCost = (20*150 + 50*160 + 30*165) / 100 = 15950 / 100 = 159.5
      expect(appleHolding!.avgCost).toBe(159.5)
    })

    it('DEPOSIT/WITHDRAW 거래는 holdings에 영향을 주지 않아야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'DEPOSIT',
          fee: 0,
          timestamp: new Date('2024-01-01')
        },
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-02')
        },
        {
          portfolioId: 1,
          type: 'WITHDRAW',
          fee: 0,
          timestamp: new Date('2024-01-03')
        }
      ]

      const holdings = calculator.processBuy(transactions)

      expect(holdings.size).toBe(1)
      expect(holdings.get('AAPL')!.totalShares).toBe(100)
    })
  })

  describe('헬퍼 함수', () => {
    it('calculateHoldingsFromTransactions가 올바르게 동작해야 함', () => {
      const transactions: Transaction[] = [
        {
          portfolioId: 1,
          type: 'BUY',
          symbol: 'AAPL',
          qty: 100,
          price: 150.0,
          fee: 1.0,
          timestamp: new Date('2024-01-01')
        }
      ]

      const holdings = calculateHoldingsFromTransactions(transactions)

      expect(holdings.size).toBe(1)
      expect(holdings.get('AAPL')!.totalShares).toBe(100)
    })
  })
})
